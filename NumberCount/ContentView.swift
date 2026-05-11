//
//  ContentView.swift
//  NumberCount
//
//  Created by NohJaisung on 5/7/26.
//


import SwiftUI
import AVFoundation
import Combine

// MARK: - 오디오 관리자

class AudioManager: ObservableObject {
    private static let bgmEnabledDefaultsKey = "bgmEnabled"

    private var bgPlayer: AVAudioPlayer?
    /// 뷰 `@State`와 분리해 재구성·라우트 변경에도 합성기가 유지되도록 함
    let guidanceSpeech = AVSpeechSynthesizer()
    /// 오답/질문패널 숫자 안내 TTS 구간 (BGM만 잠시 멈춤, 세션은 BGM과 동일하게 유지)
    private(set) var guidanceSpeechActive = false
    private var interruptionObserver: NSObjectProtocol?
    private var routeChangeObserver: NSObjectProtocol?

    @Published var isBgmEnabled: Bool = true {
        didSet {
            UserDefaults.standard.set(isBgmEnabled, forKey: Self.bgmEnabledDefaultsKey)
            syncBgmWithEnabledState()
        }
    }

    @Published var bgmVolume: Double = 0.12 {
        didSet {
            if isBgmEnabled {
                bgPlayer?.volume = Float(bgmVolume)
            }
        }
    }

    init() {
        let savedBgmOn = UserDefaults.standard.object(forKey: Self.bgmEnabledDefaultsKey) as? Bool ?? true
        isBgmEnabled = savedBgmOn
        configurePlaybackSession()
        bgPlayer = loadSound(named: "WaltzForYou")
        bgPlayer?.numberOfLoops = -1
        bgPlayer?.prepareToPlay()
        syncBgmWithEnabledState()
        observeSessionNotifications()
    }

    private func syncBgmWithEnabledState() {
        guard let p = bgPlayer else { return }
        if isBgmEnabled {
            p.volume = Float(bgmVolume)
            if !p.isPlaying {
                p.play()
            }
        } else {
            p.pause()
        }
    }

    deinit {
        if let interruptionObserver {
            NotificationCenter.default.removeObserver(interruptionObserver)
        }
        if let routeChangeObserver {
            NotificationCenter.default.removeObserver(routeChangeObserver)
        }
    }

    /// 배경·효과음용 (TTS 후 다시 호출)
    func configurePlaybackSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
            try session.setActive(true, options: [])
        } catch {
            print("AudioSession configure failed: \(error)")
            try? session.setActive(true, options: [])
        }
    }

    private func observeSessionNotifications() {
        let session = AVAudioSession.sharedInstance()
        interruptionObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: session,
            queue: .main
        ) { [weak self] notification in
            guard let self else { return }
            guard
                let info = notification.userInfo,
                let typeValue = info[AVAudioSessionInterruptionTypeKey] as? UInt,
                let type = AVAudioSession.InterruptionType(rawValue: typeValue)
            else { return }
            if type == .ended {
                self.reactivateSessionAfterExternalEvent()
            }
        }

        routeChangeObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: session,
            queue: .main
        ) { [weak self] notification in
            guard let self else { return }
            // 안내 TTS 재생 중 setCategory/setActive를 다시 호출하면 합성이 끊기는 기기가 있음
            if self.guidanceSpeechActive { return }
            guard
                let info = notification.userInfo,
                let reasonValue = info[AVAudioSessionRouteChangeReasonKey] as? UInt,
                let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue)
            else { return }
            switch reason {
            case .oldDeviceUnavailable, .newDeviceAvailable, .categoryChange, .routeConfigurationChange:
                self.reactivateSessionAfterExternalEvent()
            default:
                break
            }
        }
    }

    /// 전화·다른 앱·출력 전환 후 무음 방지
    private func reactivateSessionAfterExternalEvent() {
        configurePlaybackSession()
        if !guidanceSpeechActive {
            resumeBGM()
        }
    }

    /// 첫 숫자 읽기 전 라우트 경합 완화
    func pauseBGM() {
        bgPlayer?.pause()
    }

    func resumeBGM() {
        guard isBgmEnabled else { return }
        bgPlayer?.volume = Float(bgmVolume)
        if bgPlayer?.isPlaying == false {
            bgPlayer?.play()
        }
    }

    /// 숫자·단어 읽기 연속 재생 — BGM과 같은 playback/default 세션을 유지 (spokenAudio는 일부 기기에서 TTS만 무음)
    func beginGuidanceSpeechSequence() {
        guard !guidanceSpeechActive else { return }
        guidanceSpeechActive = true
        pauseBGM()
        configurePlaybackSession()
    }

    func endGuidanceSpeechSequence() {
        guard guidanceSpeechActive else { return }
        guidanceSpeechActive = false
        configurePlaybackSession()
        resumeBGM()
    }
    
    /// 번들에 mp3가 없을 때 wav 등으로 대체 가능 (실제 기기에서 무음 방지)
    private func loadSound(named name: String) -> AVAudioPlayer? {
        for ext in ["mp3", "wav", "m4a"] {
            if let url = Bundle.main.url(forResource: name, withExtension: ext),
               let player = try? AVAudioPlayer(contentsOf: url) {
                return player
            }
        }
        for ext in ["mp3", "wav", "m4a"] {
            let candidates = Bundle.main.urls(forResourcesWithExtension: ext, subdirectory: nil) ?? []
            if let url = candidates.first(where: { $0.deletingPathExtension().lastPathComponent == name }),
               let player = try? AVAudioPlayer(contentsOf: url) {
                return player
            }
        }
        print("Audio file not found in bundle: \(name).(mp3|wav|m4a)")
        return nil
    }

    private var feedbackClipPlayer: AVAudioPlayer?

    /// 사용자 녹음 코멘트 재생 (없으면 false)
    @discardableResult
    func playRecordedFeedback(url: URL) -> Bool {
        guard FileManager.default.fileExists(atPath: url.path) else { return false }
        do {
            stopFeedbackClip()
            feedbackClipPlayer = try AVAudioPlayer(contentsOf: url)
            feedbackClipPlayer?.volume = 1.0
            configurePlaybackSession()
            feedbackClipPlayer?.prepareToPlay()
            feedbackClipPlayer?.play()
            return true
        } catch {
            return false
        }
    }

    func stopFeedbackClip() {
        feedbackClipPlayer?.stop()
        feedbackClipPlayer = nil
    }
}

// MARK: - 게임 상태

struct GameState {
    enum QuizMode { case numberToObjects, objectsToNumber }

    var targetNumber: Int
    var options: [Int]
    var score: Int
    var theme: Theme
    var mode: QuizMode

    static func newRound(score: Int = 0, prev: Int? = nil, maxNumber: Int = 10, mode: QuizMode = .numberToObjects, themePool: [Theme]) -> GameState {
        var t = Int.random(in: 1...maxNumber)
        if let p = prev {
            var n = 0
            while t == p && n < 20 { t = Int.random(in: 1...maxNumber); n += 1 }
        }
        var pool = Array(1...maxNumber).filter { $0 != t }
        pool.shuffle()
        let count = min(3, pool.count)
        var opts = [t] + Array(pool.prefix(count))
        while opts.count < 4 {
            let extra = Int.random(in: 1...maxNumber)
            if !opts.contains(extra) { opts.append(extra) }
        }
        opts.shuffle()
        let tPool = themePool.isEmpty ? ThemeCatalog.all : themePool
        let th = tPool.randomElement() ?? ThemeCatalog.all[0]
        return GameState(
            targetNumber: t,
            options: Array(opts.prefix(4)),
            score: score,
            theme: th,
            mode: mode
        )
    }
}

let numColors: [Color] = [
    .orange, .teal, .purple, .red,
    Color(red:0.32,green:0.81,blue:0.4), Color(red:1,green:0.7,blue:0.28),
    Color(red:0.29,green:0.56,blue:0.85), Color(red:1,green:0.56,blue:0.69),
    Color(red:0.49,green:0.78,blue:0.64), .indigo
]

// MARK: - 설정 바 (난이도 + 퀴즈 모드)

struct SettingsBar: View {
    var language: AppLanguage
    @Binding var maxNumber: Int
    @Binding var quizMode: GameState.QuizMode
    @Binding var showSettings: Bool

    var body: some View {
        VStack(spacing: 10) {
            HStack {
                Button {
                    showSettings = true
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "gearshape.fill").foregroundColor(.orange)
                        Text(language.mainSettingsButton)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.orange)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(Color.orange.opacity(0.12))
                    .cornerRadius(20)
                }
                Spacer()
            }
            .padding(.horizontal, 4)

            HStack(spacing: 16) {
                HStack(spacing: 0) {
                    ForEach([5, 10], id: \.self) { n in
                        Button { maxNumber = n } label: {
                            Text("1–\(n)")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(maxNumber == n ? .white : Color(white: 0.5))
                                .padding(.horizontal, 14).padding(.vertical, 7)
                                .background(maxNumber == n ? Color.orange : Color.clear)
                                .cornerRadius(20)
                        }
                    }
                }
                .background(Color(white: 0.91)).cornerRadius(20)
                Spacer()
            }
            .padding(.horizontal, 4)

            // 퀴즈 모드 선택 (좌/우 위치 변경)
            HStack(spacing: 0) {
                Button { quizMode = .objectsToNumber } label: {
                    Text(language.modeCountToNumber)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(quizMode == .objectsToNumber ? .white : Color(white: 0.5))
                        .padding(.horizontal, 20).padding(.vertical, 12)
                        .background(quizMode == .objectsToNumber ? Color.orange : Color.clear)
                        .cornerRadius(20)
                        .minimumScaleFactor(0.7)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                }
                Button { quizMode = .numberToObjects } label: {
                    Text(language.modeNumberToCount)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(quizMode == .numberToObjects ? .white : Color(white: 0.5))
                        .padding(.horizontal, 20).padding(.vertical, 12)
                        .background(quizMode == .numberToObjects ? Color.orange : Color.clear)
                        .cornerRadius(20)
                        .minimumScaleFactor(0.7)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                }
            }
            .background(Color(white: 0.91)).cornerRadius(20)
        }
        .padding(.horizontal, 20)
    }
}

// MARK: - iPad 가로 상단 바 (모드+별 중앙 · 난이도 우)

struct PadLandscapeTopBar: View {
    var language: AppLanguage
    @Binding var maxNumber: Int
    @Binding var quizMode: GameState.QuizMode
    @Binding var showSettings: Bool
    var score: Int
    /// iPhone 가로 등 좁은 가로폭에서 폰트·여백을 줄입니다.
    var compact: Bool = false

    var body: some View {
        let modeFont: CGFloat = compact ? 14 : 18
        let modeHPad: CGFloat = compact ? 12 : 22
        let modeVPad: CGFloat = compact ? 8 : 10
        let starSize: CGFloat = compact ? 16 : 20
        let starRowH: CGFloat = compact ? 20 : 24
        let centerMinW: CGFloat = compact ? 0 : 280

        HStack(alignment: .top, spacing: compact ? 6 : 12) {
            Spacer(minLength: compact ? 4 : 8)

            VStack(spacing: compact ? 6 : 8) {
                HStack(spacing: 0) {
                    Button { quizMode = .objectsToNumber } label: {
                        Text(language.modeCountToNumber)
                            .font(.system(size: modeFont, weight: .bold))
                            .foregroundColor(quizMode == .objectsToNumber ? .white : Color(white: 0.5))
                            .padding(.horizontal, modeHPad).padding(.vertical, modeVPad)
                            .background(quizMode == .objectsToNumber ? Color.orange : Color.clear)
                            .cornerRadius(20)
                            .minimumScaleFactor(0.65)
                            .lineLimit(2)
                            .multilineTextAlignment(.center)
                    }
                    Button { quizMode = .numberToObjects } label: {
                        Text(language.modeNumberToCount)
                            .font(.system(size: modeFont, weight: .bold))
                            .foregroundColor(quizMode == .numberToObjects ? .white : Color(white: 0.5))
                            .padding(.horizontal, modeHPad).padding(.vertical, modeVPad)
                            .background(quizMode == .numberToObjects ? Color.orange : Color.clear)
                            .cornerRadius(20)
                            .minimumScaleFactor(0.65)
                            .lineLimit(2)
                            .multilineTextAlignment(.center)
                    }
                }
                .background(Color(white: 0.91)).cornerRadius(20)

                HStack(spacing: 4) {
                    ForEach(0..<min(score, 10), id: \.self) { _ in
                        Image(systemName: "star.fill").foregroundColor(.yellow).font(.system(size: starSize))
                    }
                }
                .frame(height: starRowH)
            }
            .frame(minWidth: centerMinW)

            Spacer(minLength: compact ? 4 : 8)

            HStack(spacing: 0) {
                ForEach([5, 10], id: \.self) { n in
                    Button { maxNumber = n } label: {
                        Text("1–\(n)")
                            .font(.system(size: compact ? 13 : 14, weight: .bold))
                            .foregroundColor(maxNumber == n ? .white : Color(white: 0.5))
                            .padding(.horizontal, compact ? 12 : 14).padding(.vertical, compact ? 6 : 7)
                            .background(maxNumber == n ? Color.orange : Color.clear)
                            .cornerRadius(20)
                    }
                }
            }
            .background(Color(white: 0.91)).cornerRadius(20)

            Button {
                showSettings = true
            } label: {
                Image(systemName: "gearshape.fill")
                    .foregroundColor(.orange)
                    .font(.system(size: compact ? 18 : 22))
                    .padding(6)
                    .background(Color.orange.opacity(0.12))
                    .clipShape(Circle())
            }
        }
        .padding(.horizontal, compact ? 10 : 16)
        .padding(.vertical, compact ? 6 : 8)
    }
}

// MARK: - 답변 카드

struct AnswerCard: View {
    let count: Int; let theme: Theme; let color: Color; let state: CardState
    var cardHeight: CGFloat = 155
    var forceSquare: Bool = false
    /// 1.0 기본. iPad 목표 패드 등에서 이모지 크기만 키울 때 사용합니다.
    var iconScale: CGFloat = 1.0
    var highlightedCount: Int = 0
    var emphasizeHint: Bool = false
    var useHighlightBackground: Bool = true
    var useHighlightScale: Bool = true
    enum CardState { case normal, correct, wrong }
    
    var borderColor: Color {
        switch state { case .normal: return Color(white:0.82); case .correct: return .green; case .wrong: return .red }
    }
    var bgColor: Color {
        switch state { case .normal: return .white; case .correct: return Color.green.opacity(0.12); case .wrong: return Color.red.opacity(0.12) }
    }
    func rows() -> [Int] {
        let pr = count<=3 ? count : count<=6 ? 3 : 4
        var res=[Int](); var rem=count
        while rem>0 { let n=min(rem,pr); res.append(n); rem-=n }
        return res
    }
    func rowStartIndex(_ row: Int) -> Int {
        rows().prefix(row).reduce(0, +)
    }
    var iconSize: CGFloat {
        let base: CGFloat
        if count <= 4 { base = 36 }
        else if count <= 6 { base = 32 }
        else { base = 28 }
        return min(72, base * iconScale)
    }
    var iconSpacing: CGFloat {
        let base: CGFloat
        if count <= 4 { base = 10 }
        else if count <= 6 { base = 9 }
        else { base = 8 }
        return min(18, max(6, base * iconScale))
    }
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius:24).fill(bgColor)
                .overlay(RoundedRectangle(cornerRadius:24).stroke(borderColor, lineWidth: state == .normal ? 2.5 : 4))
                .shadow(color:Color.black.opacity(0.07), radius:8, y:3)
            VStack(spacing:iconSpacing) {
                ForEach(Array(rows().enumerated()), id:\.offset) { row, n in
                    HStack(spacing:iconSpacing) {
                        ForEach(0..<n, id:\.self) { col in
                            let index = rowStartIndex(row) + col + 1
                            let active = emphasizeHint && index <= highlightedCount
                            let scale = (active && useHighlightScale) ? 1.12 : 1.0
                            let fontSize = (active && useHighlightScale) ? iconSize + 7 : iconSize
                            Text(theme.item)
                                .font(.system(size: fontSize))
                                .minimumScaleFactor(0.8)
                                .padding(active ? 4 : 1)
                                .background(
                                    Circle()
                                        .fill((active && useHighlightBackground) ? numColors[(index-1) % numColors.count].opacity(0.35) : Color.clear)
                                )
                                .scaleEffect(scale)
                                .animation(.spring(response: 0.28, dampingFraction: 0.58), value: highlightedCount)
                        }
                    }
                }
            }.padding(iconScale > 1.05 ? 18 : 14)
        }
        .modifier(CardSizeModifier(forceSquare: forceSquare, cardHeight: cardHeight))
    }
}

struct NumberOptionCard: View {
    let number: Int
    let color: Color
    let state: AnswerCard.CardState
    var cardHeight: CGFloat = 155
    var forceSquare: Bool = false
    var numberFontSize: CGFloat = 58

    var borderColor: Color {
        switch state { case .normal: return Color(white:0.82); case .correct: return .green; case .wrong: return .red }
    }
    var bgColor: Color {
        switch state { case .normal: return .white; case .correct: return Color.green.opacity(0.12); case .wrong: return Color.red.opacity(0.12) }
    }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius:24).fill(bgColor)
                .overlay(RoundedRectangle(cornerRadius:24).stroke(borderColor, lineWidth: state == .normal ? 2.5 : 4))
                .shadow(color:Color.black.opacity(0.07), radius:8, y:3)
            Text("\(number)")
                .font(.system(size: numberFontSize, weight:.black, design:.rounded))
                .foregroundColor(color)
                .minimumScaleFactor(0.7)
                .lineLimit(1)
        }
        .modifier(CardSizeModifier(forceSquare: forceSquare, cardHeight: cardHeight))
    }
}

struct CardSizeModifier: ViewModifier {
    let forceSquare: Bool
    let cardHeight: CGFloat

    func body(content: Content) -> some View {
        if forceSquare {
            // 정사각형도 전달된 side(=cardHeight)를 넘지 않도록 고정합니다.
            // aspectRatio만 쓰면 LazyVGrid 셀 너비에 맞춰 과도하게 커져 화면 밖으로 나갈 수 있습니다.
            content
                .frame(width: cardHeight, height: cardHeight)
                .frame(maxWidth: .infinity)
        } else {
            content
                .frame(maxWidth: .infinity)
                .frame(height: cardHeight)
        }
    }
}

// MARK: - 축하 화면 (별 호(arc) 비행 + 폭죽)

private struct FireworkBurst: View {
    let center: CGPoint
    let tint: Color
    var delay: Double = 0
    @State private var spread: CGFloat = 2
    @State private var opacity: Double = 0

    var body: some View {
        ZStack {
            ForEach(0..<18, id: \.self) { i in
                let angle = 2 * CGFloat.pi * CGFloat(i) / 18
                Circle()
                    .fill(tint)
                    .frame(width: 5, height: 5)
                    .offset(x: cos(angle) * spread, y: sin(angle) * spread)
                Circle()
                    .fill(Color.yellow.opacity(0.9))
                    .frame(width: 3, height: 3)
                    .offset(x: cos(angle + 0.18) * spread * 0.52, y: sin(angle + 0.18) * spread * 0.52)
            }
        }
        .position(center)
        .opacity(opacity)
        .onAppear {
            withAnimation(.easeOut(duration: 0.08).delay(delay)) {
                opacity = 1
            }
            withAnimation(.easeOut(duration: 0.52).delay(delay + 0.02)) {
                spread = 108
            }
            withAnimation(.easeIn(duration: 0.45).delay(delay + 0.38)) {
                opacity = 0
            }
        }
    }
}

struct CelebrationView: View {
    let number: Int
    let color: Color
    /// 언어에 맞는 정답 코멘트 (화면 + 음성과 동일 문구)
    var comment: String = ""
    /// 아이폰 가로: 체크와 숫자를 좌우 배치
    var isIPhoneLandscape: Bool = false

    @State private var scale: CGFloat = 0.1
    @State private var landedStars: Set<Int> = []

    private func burstCenters(width w: CGFloat, height h: CGFloat) -> [CGPoint] {
        [
            CGPoint(x: w * 0.14, y: h * 0.20),
            CGPoint(x: w * 0.86, y: h * 0.22),
            CGPoint(x: w * 0.50, y: h * 0.12),
            CGPoint(x: w * 0.10, y: h * 0.48),
            CGPoint(x: w * 0.90, y: h * 0.52),
            CGPoint(x: w * 0.22, y: h * 0.78),
            CGPoint(x: w * 0.78, y: h * 0.76),
            CGPoint(x: w * 0.50, y: h * 0.88),
        ]
    }

    /// 0…n-1 열 중 가운데부터 바깥으로 채울 때의 열 순서 (애니메이션 인덱스 → 열)
    private func starColumnCenterOutOrder(count n: Int) -> [Int] {
        let n = max(n, 1)
        if n == 1 { return [0] }
        var result: [Int] = []
        if n % 2 == 1 {
            let c = n / 2
            result.append(c)
            var d = 1
            while result.count < n {
                if c - d >= 0 { result.append(c - d) }
                if result.count >= n { break }
                if c + d < n { result.append(c + d) }
                d += 1
            }
        } else {
            let cL = n / 2 - 1
            let cR = n / 2
            result.append(cL)
            result.append(cR)
            var d = 1
            while result.count < n {
                if cL - d >= 0 { result.append(cL - d) }
                if result.count >= n { break }
                if cR + d < n { result.append(cR + d) }
                d += 1
            }
        }
        return result
    }

    /// 별을 가운데로 모은 띠 안에 두고, 착지 순서(i)는 가운데 열 → 양옆으로 퍼짐
    private func starLinePoint(index i: Int, count n: Int, width fw: CGFloat, bandHeight bh: CGFloat) -> CGPoint {
        let count = max(n, 1)
        let order = starColumnCenterOutOrder(count: count)
        let col = (i < order.count) ? order[i] : i
        let edgePad: CGFloat = 20
        let cx = fw / 2
        let y = min(bh * 0.58, bh - 28)
        if count <= 1 {
            return CGPoint(x: cx, y: y)
        }
        // 전체 폭에 펼칠 때 간격 대비, 짧게 모아서 중앙 정렬
        let usable = max(fw - 2 * edgePad, 1)
        let stepFull = usable / CGFloat(count - 1)
        let step = min(44, max(26, stepFull * 0.36))
        let clusterW = CGFloat(count - 1) * step
        var x = cx - clusterW / 2 + CGFloat(col) * step
        x = min(max(x, edgePad + 10), fw - edgePad - 10)
        return CGPoint(x: x, y: y)
    }

    private func starLaunchPoint(index i: Int, width fw: CGFloat, containerHeight ch: CGFloat, count n: Int) -> CGPoint {
        let line = starLinePoint(index: i, count: n, width: fw, bandHeight: ch)
        return CGPoint(x: line.x, y: ch + 72)
    }

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            let bursts = burstCenters(width: w, height: h)
            let checkFont: CGFloat = isIPhoneLandscape ? 76 : 100
            let numFont: CGFloat = isIPhoneLandscape ? 76 : 100
            let starBandH = min(220, h * 0.38)
            let starW = max(200, w - 48)
            let starCount = max(number, 1)

            ZStack {
                Color.white.opacity(0.88).ignoresSafeArea()

                ForEach(bursts.indices, id: \.self) { idx in
                    let pt = bursts[idx]
                    ZStack {
                        FireworkBurst(
                            center: pt,
                            tint: numColors[idx % numColors.count],
                            delay: Double(idx) * 0.09
                        )
                        FireworkBurst(
                            center: CGPoint(
                                x: pt.x + CGFloat((idx % 3) * 18 - 18),
                                y: pt.y + CGFloat(22 - (idx % 3) * 10)
                            ),
                            tint: numColors[(idx + 4) % numColors.count],
                            delay: 0.52 + Double(idx) * 0.075
                        )
                    }
                }

                // 별은 체크·숫자보다 뒤 레이어 (비행 중에도 가리지 않음)
                ZStack(alignment: .top) {
                    let starSize: CGFloat = 32
                    ForEach(0..<starCount, id: \.self) { i in
                        let endPt = starLinePoint(index: i, count: starCount, width: starW, bandHeight: starBandH)
                        let startPt = starLaunchPoint(index: i, width: starW, containerHeight: starBandH, count: starCount)
                        let arrived = landedStars.contains(i)
                        Image(systemName: "star.fill")
                            .font(.system(size: starSize))
                            .foregroundColor(numColors[i % numColors.count])
                            .shadow(color: numColors[i % numColors.count].opacity(0.45), radius: 4, y: 2)
                            .rotationEffect(.degrees(arrived ? Double(i * 14) : -40))
                            .position(x: arrived ? endPt.x : startPt.x, y: arrived ? endPt.y : startPt.y)
                    }
                }
                .frame(width: starW, height: starBandH)
                .position(x: w / 2, y: h * 0.74)
                .allowsHitTesting(false)

                Group {
                    if isIPhoneLandscape {
                        VStack(spacing: 14) {
                            HStack(alignment: .center, spacing: 22) {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: checkFont))
                                    .foregroundColor(.green)
                                    .shadow(color: .green.opacity(0.35), radius: 12, y: 4)
                                Text("\(number)")
                                    .font(.system(size: numFont, weight: .black, design: .rounded))
                                    .foregroundColor(color)
                            }
                            if !comment.isEmpty {
                                Text(comment)
                                    .font(.system(size: 22, weight: .bold, design: .rounded))
                                    .foregroundColor(Color.green.opacity(0.92))
                                    .multilineTextAlignment(.center)
                            }
                        }
                    } else {
                        VStack(spacing: 18) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: checkFont))
                                .foregroundColor(.green)
                                .shadow(color: .green.opacity(0.35), radius: 12, y: 4)
                            Text("\(number)")
                                .font(.system(size: numFont, weight: .black, design: .rounded))
                                .foregroundColor(color)
                            if !comment.isEmpty {
                                Text(comment)
                                    .font(.system(size: 26, weight: .bold, design: .rounded))
                                    .foregroundColor(Color.green.opacity(0.92))
                                    .multilineTextAlignment(.center)
                            }
                        }
                    }
                }
                .padding(.horizontal, isIPhoneLandscape ? 20 : 28)
                .padding(.vertical, isIPhoneLandscape ? 16 : 22)
                .background(
                    RoundedRectangle(cornerRadius: 28, style: .continuous)
                        .fill(Color.white.opacity(0.94))
                        .shadow(color: Color.black.opacity(0.1), radius: 14, y: 5)
                )
                .scaleEffect(scale)
                .position(x: w / 2, y: h * 0.46)
            }
        }
        .onAppear {
            landedStars.removeAll()
            scale = 0.1
            withAnimation(.spring(response: 0.5, dampingFraction: 0.62)) {
                scale = 1.0
            }
            for i in 0..<max(number, 1) {
                let delay = 0.12 + Double(i) * 0.06
                DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
                    withAnimation(.interpolatingSpring(stiffness: 118, damping: 14)) {
                        _ = landedStars.insert(i)
                    }
                }
            }
        }
    }
}

// MARK: - 메인 화면

struct ContentView: View {
    @StateObject private var audio = AudioManager()
    @StateObject private var feedbackRecorder = AnswerFeedbackRecorder()
    @State private var maxNumber: Int = 5
    // 앱 시작 시: "갯수 - 숫자"
    @State private var selectedMode: GameState.QuizMode = .objectsToNumber
    @State private var game = GameState.newRound(mode: .objectsToNumber, themePool: ThemeCatalog.defaultPool)
    @State private var selected: Int? = nil
    @State private var isCorrect: Bool? = nil
    @State private var showCelebration = false
    @State private var wrongIndex: Int? = nil
    @State private var shaking = false
    @State private var locked = false
    @State private var numScale: CGFloat = 0.3
    @State private var showingCountHint = false
    @State private var highlightedCount = 0
    @State private var hintWord = ""
    /// 안내 시퀀스 안에서 몇 번째 발화인지 (첫 발화에 짧은 프라임 TTS 적용)
    @State private var guidanceUtteranceCount = 0
    @State private var showSettings = false
    @AppStorage(AppLanguage.storageKey) private var appLanguageRaw: String = AppLanguage.korean.rawValue
    @AppStorage(ItemCategory.appStorageKey) private var themeCategoriesStorage: String = ItemCategory.defaultStorageValue

    private var appLanguage: AppLanguage {
        AppLanguage(rawValue: appLanguageRaw) ?? .korean
    }

    private var themePoolForRound: [Theme] {
        ThemeCatalog.pool(for: ItemCategory.decodeSet(from: themeCategoriesStorage))
    }

    var numColor: Color { numColors[(game.targetNumber-1) % numColors.count] }
    var theme: Theme { game.theme }
    
    @ViewBuilder
    private func mainQuizLayout(geo: GeometryProxy) -> some View {
            let safe = geo.safeAreaInsets
            let layoutW = geo.size.width - safe.leading - safe.trailing
            let layoutH = geo.size.height - safe.top - safe.bottom
            let isPadLayout = layoutW >= 768
            let isLandscape = layoutW > layoutH
            /// iPhone·iPod 가로: 세로 스택 대신 좌(문제)·우(보기) 분할
            let phoneLandscapeSplit = !isPadLayout && isLandscape
            let minSide = min(layoutW, layoutH)
            let maxSide = max(layoutW, layoutH)
            let isPadPro105 = isPadLayout && minSide >= 834 && maxSide <= 1112
            let isCompactPad = isPadLayout && maxSide <= 1112   // iPad mini / iPad Pro 10.5 포함
            let isLargePad = isPadLayout && maxSide >= 1366
            let isCompactHeight = layoutH < 820
            let isNumberToObjectsMode = game.mode == .numberToObjects
            let gridSpacing: CGFloat = isPadLayout
                ? (
                    isPadPro105
                    ? (isLandscape ? 10 : 14)
                    : (isCompactPad ? (isLandscape ? 10 : 12) : (isLandscape ? 12 : 16))
                )
                : (isCompactHeight ? 12 : 16)

            let gridHorizontalPadding: CGFloat = 32
            // iPad 세로·가로 공통: PadLandscapeTopBar + 좌(문제) | 우(답안 2×2). 폭은 긴 변 기준으로 가로와 같은 비율을 쓰고 layoutW로 클램프
            let padSplitQuizLayout = isPadLayout
            /// 아이패드 세로: 문제 패널(위) · 답안 패널(아래) 상하 배치
            let padPortraitStack = padSplitQuizLayout && !isLandscape
            let splitQuizWideLayout = padSplitQuizLayout || phoneLandscapeSplit
            let colGap: CGFloat = phoneLandscapeSplit ? 12 : 18
            let padPortraitPanelGap: CGFloat = 16
            let landscapeTopBarH: CGFloat = phoneLandscapeSplit ? 86 : 118
            let padLongSide = max(layoutW, layoutH)
            let padShortSide = min(layoutW, layoutH)
            let landscapeMainHPad: CGFloat = phoneLandscapeSplit ? 12 : 28
            let rightPanelW: CGFloat = {
                if phoneLandscapeSplit {
                    return min(max(layoutW * 0.46, 236), 312)
                }
                if padSplitQuizLayout {
                    if padPortraitStack {
                        return max(300, layoutW - landscapeMainHPad * 2)
                    }
                    let rightIdeal = isNumberToObjectsMode
                        ? min(padLongSide * 0.48, padShortSide * 0.72, 700)
                        : min(padLongSide * 0.44, padShortSide * 0.66, 640)
                    let minLeftQuiz: CGFloat = 260
                    let maxRight = max(240, layoutW - colGap - landscapeMainHPad * 2 - minLeftQuiz)
                    return min(rightIdeal, maxRight)
                }
                return 0
            }()
            let leftContentAvailW = splitQuizWideLayout
                ? (
                    padPortraitStack
                    ? max(120, layoutW - landscapeMainHPad * 2)
                    : max(120, layoutW - rightPanelW - colGap - landscapeMainHPad * 2)
                )
                : 0
            let phoneLandGridGap: CGFloat = isCompactHeight ? 10 : 12
            let landscapeAnswerGridGap: CGFloat = {
                if phoneLandscapeSplit { return phoneLandGridGap }
                if padSplitQuizLayout {
                    return isLargePad ? 22 : (isPadPro105 ? 18 : (isCompactPad ? 18 : 20))
                }
                return 12
            }()

            let gridColumnCount: CGFloat = 2
            let columnWidth = (layoutW - gridHorizontalPadding - gridSpacing) / gridColumnCount

            // iPad(세로)·iPhone: 2×2 그리드용 세로 예산. 가로 3열 레이아웃은 아래 optionCardHeight에서 별도 계산.
            let vstackSpacing: CGFloat = isPadLayout ? (isPadPro105 ? (isLandscape ? 7 : 11) : (isCompactPad ? (isLandscape ? 6 : 10) : (isLandscape ? 8 : 14))) : (isCompactHeight ? 10 : 14)
            let topBarPadding: CGFloat = isPadLayout ? (isPadPro105 ? (isLandscape ? 3 : 10) : (isCompactPad ? (isLandscape ? 2 : 8) : (isLandscape ? 4 : 14))) : (isCompactHeight ? 8 : 16)
            let settingsReserve: CGFloat = isPadLayout ? (isLandscape ? 118 : 168) : 0
            let scoreReserve: CGFloat = 28
            let hintReserve: CGFloat = 44
            let targetCap: CGFloat = isPadLayout && !padSplitQuizLayout
                ? min(
                    isLandscape ? 340 : 380,
                    layoutH * (isLandscape ? 0.40 : 0.34),
                    layoutW * (isLandscape ? 0.42 : 0.52)
                )
                : 0
            let aboveGridReserve: CGFloat = isPadLayout && !padSplitQuizLayout
                ? (
                    topBarPadding + settingsReserve + vstackSpacing + scoreReserve + vstackSpacing
                    + targetCap + vstackSpacing + hintReserve + vstackSpacing + 16
                )
                : 0
            let gridVerticalBudget = isPadLayout && !padSplitQuizLayout ? max(140, layoutH - aboveGridReserve) : 0
            let rowHeightBudget = isPadLayout && !padSplitQuizLayout
                ? max(120, (gridVerticalBudget - gridSpacing) / 2)
                : 0
            let sideCap: CGFloat = isPadLayout
                ? (
                    isPadPro105
                        ? (isLandscape ? 250 : 292)
                        : (isCompactPad ? (isLandscape ? 248 : 288) : (isLandscape ? 300 : (isLargePad ? 360 : 330)))
                )
                : 0
            let optionCardHeight: CGFloat = {
                if phoneLandscapeSplit {
                    let margin: CGFloat = 8
                    let gridW = max(120, rightPanelW - margin * 2)
                    let gridH = max(140, layoutH - landscapeTopBarH - margin * 2)
                    let gap = landscapeAnswerGridGap
                    let colW = max(68, (gridW - gap) / 2)
                    let rowH = max(68, (gridH - gap) / 2)
                    let cell = min(colW, rowH)
                    if isNumberToObjectsMode {
                        return min(max(cell, 78), 168)
                    }
                    return min(max(cell, 72), 158)
                }
                if padSplitQuizLayout {
                    let margin: CGFloat = 16
                    let gap = landscapeAnswerGridGap
                    if padPortraitStack {
                        let gridW = max(240, layoutW - margin * 2 - landscapeMainHPad * 2)
                        let bodyBelowBar = layoutH - landscapeTopBarH - padPortraitPanelGap - margin * 2
                        let gridH = max(220, bodyBelowBar * 0.52)
                        let colW = max(100, (gridW - gap) / 2)
                        let rowH = max(100, (gridH - gap) / 2)
                        let cell = min(colW, rowH)
                        if isNumberToObjectsMode {
                            return min(max(cell, 140), 320)
                        }
                        return min(max(cell, 128), 300)
                    }
                    let gridW = max(280, rightPanelW - margin * 2)
                    // 세로에서도 가로와 비슷한 셀 크기: 행 높이는 짧은 변 기준으로 한 번 더 맞춤
                    let gridHFromLayout = layoutH - landscapeTopBarH - margin * 2
                    let gridHCap = padShortSide - landscapeTopBarH - margin * 2
                    let gridH = max(360, min(gridHFromLayout, max(360, gridHCap * 1.15)))
                    let colW = max(180, (gridW - gap) / 2)
                    let rowH = max(180, (gridH - gap) / 2)
                    let cell = min(colW, rowH)
                    if isNumberToObjectsMode {
                        return min(max(cell, 220), 560)
                    }
                    return min(max(cell, 200), 520)
                }
                if isPadLayout {
                    return min(columnWidth, rowHeightBudget, sideCap)
                }
                return isCompactHeight ? 132 : 155
            }()
            let landscapeNumberFont = max(
                22,
                min(padSplitQuizLayout ? 112 : 46, optionCardHeight * (padSplitQuizLayout ? 0.44 : 0.36))
            )
            let padAnswerGridContentWidth: CGFloat = padSplitQuizLayout
                ? (optionCardHeight * 2 + landscapeAnswerGridGap)
                : 0
            /// 우측 패널 안에서 2×2를 살짝 가운데 쪽으로 (이전: slack 전부 그리드 왼쪽)
            let padAnswerPanelSlack: CGFloat = padSplitQuizLayout
                ? max(0, rightPanelW - padAnswerGridContentWidth)
                : 0
            let padAnswerLeadClear: CGFloat = padSplitQuizLayout ? padAnswerPanelSlack * 0.68 : 0
            let padAnswerTrailClear: CGFloat = padSplitQuizLayout ? padAnswerPanelSlack * 0.32 : 0
            let targetHeight: CGFloat = {
                if padSplitQuizLayout || phoneLandscapeSplit {
                    if padPortraitStack {
                        let availW = leftContentAvailW
                        let topBudget = (layoutH - landscapeTopBarH - padPortraitPanelGap - 20) * 0.42
                        let s = min(availW * 0.86, topBudget, layoutW * 0.72)
                        let base = max(200, min(s, 460))
                        return isNumberToObjectsMode ? max(170, base * 0.82) : base
                    }
                    let availW = leftContentAvailW
                    let hintBlk: CGFloat = phoneLandscapeSplit ? 34 : 44
                    let bottomMargin: CGFloat = phoneLandscapeSplit ? 14 : 24
                    let availH = layoutH - landscapeTopBarH - hintBlk - bottomMargin
                    let capH = phoneLandscapeSplit ? 0.66 : 0.72
                    let s = min(availW * 0.90, availH * 0.88, layoutH * capH)
                    let lo: CGFloat = phoneLandscapeSplit ? 130 : 220
                    let hi: CGFloat = phoneLandscapeSplit ? 320 : 520
                    let base = max(lo, min(s, hi))
                    return isNumberToObjectsMode ? max(phoneLandscapeSplit ? 112 : 180, base * 0.80) : base
                }
                if isPadLayout {
                    return min(
                        targetCap,
                        max(
                            isPadPro105 ? (isLandscape ? 200 : 232) : (isCompactPad ? (isLandscape ? 192 : 224) : (isLandscape ? 218 : 258)),
                            optionCardHeight + (isPadPro105 ? (isLandscape ? 14 : 22) : 18)
                        )
                    )
                }
                return isCompactHeight ? 140 : 155
            }()
            let targetWidth: CGFloat = {
                if padSplitQuizLayout || phoneLandscapeSplit {
                    return min(targetHeight, leftContentAvailW * 0.96)
                }
                if isPadLayout { return targetHeight }
                return min(layoutW * 0.62, 230)
            }()
            let padTargetInnerPad: CGFloat = isPadLayout ? 22 : (phoneLandscapeSplit ? 10 : 12)
            let padTargetNumFont: CGFloat = isPadLayout
                ? min(220, max(130, targetHeight * 0.58))
                : (phoneLandscapeSplit ? min(200, max(86, targetHeight * 0.5)) : (isCompactHeight ? 94 : 110))
            let padTargetIconScale: CGFloat = (isPadLayout || phoneLandscapeSplit)
                ? min(2.05, max(phoneLandscapeSplit ? 1.05 : 1.2, (targetHeight - padTargetInnerPad * 2) / 120))
                : 1.0
            let padTargetCorner: CGFloat = isPadLayout ? 48 : (phoneLandscapeSplit ? 40 : 36)
            let padTargetStroke: CGFloat = isPadLayout ? 6 : 5
            let padHintFont: CGFloat = isPadLayout
                ? min(42, max(30, targetHeight * 0.15))
                : (phoneLandscapeSplit ? min(30, max(20, targetHeight * 0.13)) : (isCompactHeight ? 24 : 30))
            let padHintEmoji: CGFloat = isPadLayout
                ? min(40, max(28, targetHeight * 0.14))
                : (phoneLandscapeSplit ? min(28, max(20, targetHeight * 0.11)) : (isCompactHeight ? 24 : 28))

            ZStack {
                Color(red:1,green:0.97,blue:0.91).ignoresSafeArea()
                Group {
                    if splitQuizWideLayout {
                        VStack(spacing: phoneLandscapeSplit ? 8 : 12) {
                            PadLandscapeTopBar(
                                language: appLanguage,
                                maxNumber: $maxNumber,
                                quizMode: $selectedMode,
                                showSettings: $showSettings,
                                score: game.score,
                                compact: phoneLandscapeSplit
                            )

                            Group {
                                let padProblemColumn: some View = VStack(spacing: 10) {
                                    Spacer(minLength: 8)
                                    ZStack {
                                        RoundedRectangle(cornerRadius: padTargetCorner).fill(Color.white)
                                            .overlay(
                                                RoundedRectangle(cornerRadius: padTargetCorner)
                                                    .stroke(showingCountHint ? .orange : numColor, lineWidth: showingCountHint ? max(8, padTargetStroke + 2) : padTargetStroke)
                                            )
                                            .shadow(color: Color.black.opacity(0.1), radius: 12, y: 5)
                                        if game.mode == .numberToObjects {
                                            Text("\(game.targetNumber)")
                                                .font(.system(size: padTargetNumFont, weight: .black, design: .rounded))
                                                .foregroundColor(numColor)
                                                .minimumScaleFactor(0.75)
                                                .lineLimit(1)
                                        } else {
                                            AnswerCard(
                                                count: game.targetNumber,
                                                theme: theme,
                                                color: numColor,
                                                state: .normal,
                                                cardHeight: targetHeight - padTargetInnerPad * 2,
                                                forceSquare: padSplitQuizLayout && isLandscape,
                                                iconScale: padTargetIconScale,
                                                highlightedCount: highlightedCount,
                                                emphasizeHint: showingCountHint
                                            )
                                            .padding(padTargetInnerPad)
                                        }
                                    }
                                    .frame(width: targetWidth, height: targetHeight)
                                    .scaleEffect(numScale)
                                    .animation(.spring(response: 0.5, dampingFraction: 0.6), value: numScale)
                                    .animation(.spring(response: 0.35, dampingFraction: 0.62), value: showingCountHint)
                                    .contentShape(Rectangle())
                                    .onTapGesture { onQuestionPanelTapped() }

                                    if showingCountHint, !hintWord.isEmpty {
                                        Group {
                                            if game.mode == .numberToObjects {
                                                HStack(spacing: 10) {
                                                    Text(hintWord.uppercased())
                                                        .font(.system(size: padHintFont, weight: .black, design: .rounded))
                                                    HStack(spacing: 4) {
                                                        ForEach(0..<highlightedCount, id: \.self) { _ in
                                                            Text(theme.item)
                                                                .font(.system(size: padHintEmoji))
                                                        }
                                                    }
                                                }
                                                .foregroundColor(.orange)
                                            } else {
                                                Text(hintWord.uppercased())
                                                    .font(.system(size: padHintFont, weight: .black, design: .rounded))
                                                    .foregroundColor(.orange)
                                            }
                                        }
                                        .transition(.opacity.combined(with: .scale))
                                    }
                                    Spacer(minLength: 8)
                                }

                                let padAnswerColumn: some View = {
                                    let answerColumns = [
                                        GridItem(.flexible(), spacing: landscapeAnswerGridGap),
                                        GridItem(.flexible())
                                    ]
                                    let answerGrid = LazyVGrid(columns: answerColumns, spacing: landscapeAnswerGridGap) {
                                        ForEach(0..<4, id: \.self) { idx in
                                            let opt = game.options[idx]
                                            let st: AnswerCard.CardState = {
                                                guard selected == opt else { return .normal }
                                                if let c = isCorrect { return c ? .correct : .wrong }
                                                return .normal
                                            }()
                                            Group {
                                                if game.mode == .numberToObjects {
                                                    AnswerCard(
                                                        count: opt,
                                                        theme: theme,
                                                        color: theme.colors[idx % 4],
                                                        state: st,
                                                        cardHeight: optionCardHeight,
                                                        forceSquare: false,
                                                        highlightedCount: highlightedCount,
                                                        emphasizeHint: showingCountHint && opt == game.targetNumber && game.mode != .numberToObjects,
                                                        useHighlightBackground: game.mode != .numberToObjects,
                                                        useHighlightScale: game.mode != .numberToObjects
                                                    )
                                                } else {
                                                    NumberOptionCard(
                                                        number: opt,
                                                        color: theme.colors[idx % 4],
                                                        state: st,
                                                        cardHeight: optionCardHeight,
                                                        forceSquare: false,
                                                        numberFontSize: landscapeNumberFont
                                                    )
                                                }
                                            }
                                            .offset(x: (wrongIndex == idx && shaking) ? 10 : 0)
                                            .animation(
                                                (wrongIndex == idx && shaking)
                                                ? Animation.easeInOut(duration: 0.07).repeatCount(5, autoreverses: true)
                                                : .default,
                                                value: shaking
                                            )
                                            .onTapGesture { tap(option: opt, index: idx) }
                                        }
                                    }

                                    return Group {
                                        if padSplitQuizLayout {
                                            if padPortraitStack {
                                                HStack(alignment: .center, spacing: 0) {
                                                    Spacer(minLength: 0)
                                                    answerGrid
                                                        .frame(width: padAnswerGridContentWidth)
                                                    Spacer(minLength: 0)
                                                }
                                                .frame(maxWidth: .infinity)
                                                .padding(.horizontal, landscapeMainHPad)
                                            } else {
                                                HStack(alignment: .center, spacing: 0) {
                                                    Color.clear.frame(width: padAnswerLeadClear)
                                                    answerGrid
                                                        .frame(width: padAnswerGridContentWidth)
                                                    Color.clear.frame(width: padAnswerTrailClear)
                                                }
                                                .frame(width: rightPanelW)
                                                .padding(.leading, 0)
                                                .padding(.trailing, 10)
                                            }
                                        } else {
                                            answerGrid
                                                .frame(width: rightPanelW)
                                                .padding(.leading, 4)
                                                .padding(.trailing, phoneLandscapeSplit ? 14 : 28)
                                        }
                                    }
                                }()

                                if padPortraitStack {
                                    VStack(spacing: padPortraitPanelGap) {
                                        padProblemColumn
                                            .frame(maxWidth: .infinity)
                                            .frame(maxHeight: .infinity, alignment: .center)
                                        padAnswerColumn
                                            .frame(maxWidth: .infinity)
                                            .frame(maxHeight: .infinity, alignment: .center)
                                    }
                                } else {
                                    HStack(alignment: .center, spacing: colGap) {
                                        padProblemColumn
                                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                                        padAnswerColumn
                                            .frame(maxHeight: .infinity)
                                    }
                                }
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                        .frame(width: layoutW, height: layoutH)
                        .padding(.top, safe.top)
                        .padding(.bottom, safe.bottom)
                        .padding(.leading, safe.leading)
                        .padding(.trailing, safe.trailing)
                    } else {
                        VStack(spacing: isPadLayout ? (isPadPro105 ? (isLandscape ? 7 : 11) : (isCompactPad ? (isLandscape ? 6 : 10) : (isLandscape ? 8 : 14))) : (isCompactHeight ? 10 : 14)) {
                            // ① 설정 바
                            SettingsBar(language: appLanguage, maxNumber: $maxNumber, quizMode: $selectedMode, showSettings: $showSettings)
                                .padding(.top, isPadLayout ? (isPadPro105 ? (isLandscape ? 3 : 10) : (isCompactPad ? (isLandscape ? 2 : 8) : (isLandscape ? 4 : 14))) : (isCompactHeight ? 8 : 16))

                            // ② 점수
                            HStack(spacing:4) {
                                ForEach(0..<min(game.score,10),id:\.self) { _ in
                                    Image(systemName:"star.fill").foregroundColor(.yellow).font(.system(size:20))
                                }
                            }.frame(height:24)

                            // ③ 목표 숫자/물건
                            ZStack {
                                RoundedRectangle(cornerRadius: padTargetCorner).fill(Color.white)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: padTargetCorner)
                                            .stroke(showingCountHint ? .orange : numColor, lineWidth: showingCountHint ? max(8, padTargetStroke + 2) : padTargetStroke)
                                    )
                                    .shadow(color:Color.black.opacity(0.1),radius:12,y:5)
                                if game.mode == .numberToObjects {
                                    Text("\(game.targetNumber)")
                                        .font(.system(size: padTargetNumFont, weight: .black, design: .rounded))
                                        .foregroundColor(numColor)
                                        .minimumScaleFactor(0.75)
                                        .lineLimit(1)
                                } else {
                                    AnswerCard(
                                        count: game.targetNumber,
                                        theme: theme,
                                        color: numColor,
                                        state: .normal,
                                        cardHeight: targetHeight - padTargetInnerPad * 2,
                                        forceSquare: isPadLayout,
                                        iconScale: padTargetIconScale,
                                        highlightedCount: highlightedCount,
                                        emphasizeHint: showingCountHint
                                    )
                                        .padding(padTargetInnerPad)
                                }
                            }
                            .frame(width: targetWidth, height: targetHeight)
                            .scaleEffect(numScale)
                            .animation(.spring(response:0.5,dampingFraction:0.6),value:numScale)
                            .animation(.spring(response:0.35,dampingFraction:0.62), value: showingCountHint)
                            .contentShape(Rectangle())
                            .onTapGesture { onQuestionPanelTapped() }

                            if showingCountHint, !hintWord.isEmpty {
                                Group {
                                    if game.mode == .numberToObjects {
                                        HStack(spacing: 10) {
                                            Text(hintWord.uppercased())
                                                .font(.system(size: padHintFont, weight: .black, design: .rounded))
                                            HStack(spacing: 4) {
                                                ForEach(0..<highlightedCount, id: \.self) { _ in
                                                    Text(theme.item)
                                                        .font(.system(size: padHintEmoji))
                                                }
                                            }
                                        }
                                        .foregroundColor(.orange)
                                    } else {
                                        Text(hintWord.uppercased())
                                            .font(.system(size: padHintFont, weight: .black, design: .rounded))
                                            .foregroundColor(.orange)
                                    }
                                }
                                .transition(.opacity.combined(with: .scale))
                            }

                            // ④ 답변 그리드 (2×2)
                            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: gridSpacing) {
                                ForEach(0..<4,id:\.self) { idx in
                                    let opt = game.options[idx]
                                    let st: AnswerCard.CardState = {
                                        guard selected==opt else { return .normal }
                                        if let c=isCorrect { return c ? .correct : .wrong }
                                        return .normal
                                    }()
                                    Group {
                                        if game.mode == .numberToObjects {
                                            AnswerCard(
                                                count:opt,
                                                theme:theme,
                                                color:theme.colors[idx%4],
                                                state:st,
                                                cardHeight: optionCardHeight,
                                                forceSquare: isPadLayout,
                                                highlightedCount: highlightedCount,
                                                emphasizeHint: showingCountHint && opt == game.targetNumber && game.mode != .numberToObjects,
                                                useHighlightBackground: game.mode != .numberToObjects,
                                                useHighlightScale: game.mode != .numberToObjects
                                            )
                                        } else {
                                            NumberOptionCard(
                                                number: opt,
                                                color: theme.colors[idx%4],
                                                state: st,
                                                cardHeight: optionCardHeight,
                                                forceSquare: isPadLayout
                                            )
                                        }
                                    }
                                        .offset(x:(wrongIndex==idx && shaking) ? 10 : 0)
                                        .animation(
                                            (wrongIndex==idx && shaking)
                                            ? Animation.easeInOut(duration:0.07).repeatCount(5,autoreverses:true)
                                            : .default,
                                            value: shaking
                                        )
                                        .onTapGesture { tap(option:opt, index:idx) }
                                }
                            }.padding(.horizontal,16)

                            Spacer(minLength: isCompactHeight ? 6 : 12)
                        }
                    }
                }
                if showCelebration {
                    CelebrationView(
                        number: game.targetNumber,
                        color: numColor,
                        comment: appLanguage.feedbackCorrectPhrase,
                        isIPhoneLandscape: phoneLandscapeSplit
                    )
                    .transition(.opacity)
                }
            }
    }

    var body: some View {
        GeometryReader { geo in
            mainQuizLayout(geo: geo)
        }
        .onChange(of: maxNumber) { nextRound() }
        .onChange(of: selectedMode) { nextRound() }
        .onChange(of: themeCategoriesStorage) { nextRound() }
        .sheet(isPresented: $showSettings) {
            NavigationStack {
                SettingsView(
                    audio: audio,
                    feedbackRecorder: feedbackRecorder
                )
            }
        }
        .onAppear {
            withAnimation { numScale = 1.0 }
            feedbackRecorder.restorePlaybackSession = {
                audio.configurePlaybackSession()
                audio.resumeBGM()
            }
        }
    }
    
    func nextRound(prev: Int? = nil) {
        if audio.guidanceSpeechActive {
            audio.endGuidanceSpeechSequence()
        }
        game = GameState.newRound(score: game.score, prev: prev, maxNumber: maxNumber, mode: selectedMode, themePool: themePoolForRound)
        selected=nil; isCorrect=nil; wrongIndex=nil; locked=false
        showingCountHint=false; highlightedCount=0; hintWord=""
        numScale=0.3; withAnimation { numScale=1.0 }
    }

    /// 오답 안내에 표시할 숫자 (1, 2, 3 …)
    func countHintDisplay(for number: Int) -> String {
        "\(number)"
    }

    /// 한국어 숫자 읽기 (일, 이, 삼 …) — TTS용
    func koreanNumberWord(for number: Int) -> String {
        let native = [
            "", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구", "십"
        ]
        guard number >= 1 else { return "\(number)" }
        if number < native.count { return native[number] }
        if number < 20 { return "십" + (number == 10 ? "" : native[number % 10]) }
        return "\(number)"
    }

    /// 영어 숫자 읽기 (one, two, …) — TTS·힌트용
    func englishNumberWord(for number: Int) -> String {
        let words = [
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"
        ]
        guard number >= 1, number < words.count else { return "\(number)" }
        return words[number]
    }

    /// 정답·오답 코멘트: 녹음 파일이 있으면 트림된 파일 재생, 없으면 TTS
    private func playAnswerFeedback(kind: AnswerFeedbackKind) {
        let fallback: String
        switch kind {
        case .correct: fallback = appLanguage.feedbackCorrectPhrase
        case .wrong: fallback = appLanguage.feedbackWrongPhrase
        }
        if let url = try? AnswerFeedbackRecorder.savedFileURL(kind: kind, language: appLanguage),
           audio.playRecordedFeedback(url: url) {
            return
        }
        speakAnswerFeedbackTTS(fallback)
    }

    private func speakAnswerFeedbackTTS(_ text: String) {
        audio.configurePlaybackSession()
        audio.stopFeedbackClip()
        let synth = audio.guidanceSpeech
        synth.stopSpeaking(at: .immediate)
        let voice = voiceForGuidanceTTS()
        let u = AVSpeechUtterance(string: text)
        u.voice = voice
        u.volume = 1.0
        u.rate = Float(appLanguage == .english ? 0.5 : 0.4)
        u.pitchMultiplier = 1.0
        u.preUtteranceDelay = 0.06
        u.postUtteranceDelay = 0.08
        synth.speak(u)
    }

    /// 시스템에 설치된 음성이 없을 때를 대비해 언어 코드·접두사로 대체
    private func voiceForGuidanceTTS() -> AVSpeechSynthesisVoice? {
        let langCode = appLanguage == .english ? "en-US" : "ko-KR"
        if let v = AVSpeechSynthesisVoice(language: langCode) { return v }
        let prefix = String(langCode.prefix(2))
        return AVSpeechSynthesisVoice.speechVoices().first { $0.language.hasPrefix(prefix) }
    }

    func speakGuidance(_ text: String) {
        let inGuidance = audio.guidanceSpeechActive
        let synth = audio.guidanceSpeech
        audio.stopFeedbackClip()
        if inGuidance {
            audio.configurePlaybackSession()
        }
        let voice = voiceForGuidanceTTS()
        let isFirstInSequence = inGuidance && guidanceUtteranceCount == 0

        synth.stopSpeaking(at: .immediate)
        guidanceUtteranceCount += 1

        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = voice
        utterance.volume = 1.0
        utterance.rate = Float(appLanguage == .english ? 0.48 : 0.38)
        utterance.pitchMultiplier = 1.0
        if isFirstInSequence {
            utterance.preUtteranceDelay = 0.22
        } else {
            utterance.preUtteranceDelay = inGuidance ? 0.08 : 0.03
        }
        utterance.postUtteranceDelay = 0.18
        synth.speak(utterance)
    }

    /// 질문 패널 탭: 오답 시와 같이 숫자·단어를 읽어 줌 (효과음/흔들림 없음)
    func onQuestionPanelTapped() {
        guard !showCelebration else { return }
        guard !showingCountHint else { return }
        guard selected == nil else { return }
        guard !locked else { return }
        locked = true
        startWrongCountGuidance(fromWrongAnswerFlow: false)
    }

    func startWrongCountGuidance(fromWrongAnswerFlow: Bool = true) {
        showingCountHint = true
        highlightedCount = 0
        hintWord = ""
        guidanceUtteranceCount = 0
        let total = game.targetNumber

        func step(_ n: Int) {
            guard n <= total else {
                // 개수와 관계없이 읽기·힌트는 항상 단수형만 사용
                let finalLabel = appLanguage == .english ? theme.itemWordSingular : theme.itemWordSingularKO
                withAnimation(.spring(response: 0.32, dampingFraction: 0.62)) {
                    hintWord = appLanguage == .english ? finalLabel.uppercased() : finalLabel
                }
                speakGuidance(finalLabel)
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.15) {
                    audio.endGuidanceSpeechSequence()
                    withAnimation {
                        showingCountHint = false
                        highlightedCount = 0
                        hintWord = ""
                    }
                    selected = nil
                    isCorrect = nil
                    wrongIndex = nil
                    locked = false
                }
                return
            }

            let spokenNumber = appLanguage == .english ? englishNumberWord(for: n) : koreanNumberWord(for: n)
            let display = appLanguage == .english ? spokenNumber.uppercased() : countHintDisplay(for: n)
            withAnimation(.spring(response: 0.32, dampingFraction: 0.62)) {
                highlightedCount = n
                hintWord = display
            }
            speakGuidance(spokenNumber)

            DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) {
                // 천천히 또렷하게 들리도록 숫자 간 간격을 넉넉히 둡니다.
                step(n + 1)
            }
        }

        let lead: TimeInterval = fromWrongAnswerFlow ? 0.48 : 0.28
        DispatchQueue.main.asyncAfter(deadline: .now() + lead) {
            audio.beginGuidanceSpeechSequence()
            step(1)
        }
    }
    
    func tap(option: Int, index: Int) {
        guard !locked, selected==nil else { return }
        locked=true; selected=option
        let ok = option==game.targetNumber; isCorrect=ok
        if ok {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.08) {
                playAnswerFeedback(kind: .correct)
            }
            withAnimation { showCelebration=true }
            DispatchQueue.main.asyncAfter(deadline:.now()+2.5) {
                let s=game.score+1
                withAnimation { showCelebration=false }
                DispatchQueue.main.asyncAfter(deadline:.now()+0.3) {
                    game = GameState.newRound(score: s, prev: game.targetNumber, maxNumber: maxNumber, mode: selectedMode, themePool: themePoolForRound)
                    selected=nil; isCorrect=nil; locked=false
                    numScale=0.3; withAnimation { numScale=1.0 }
                }
            }
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                playAnswerFeedback(kind: .wrong)
            }
            wrongIndex=index
            withAnimation { shaking=true }
            DispatchQueue.main.asyncAfter(deadline:.now()+0.6) {
                withAnimation { shaking=false }
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                    startWrongCountGuidance()
                }
            }
        }
    }
}

#Preview {
    ContentView()
}
