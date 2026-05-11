//
//  SettingsView.swift
//  NumberCount
//
//  메인 화면과 같은 크림 배경·흰 카드·오렌지 포인트 스타일
//

import SwiftUI

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var audio: AudioManager
    @ObservedObject var feedbackRecorder: AnswerFeedbackRecorder

    @AppStorage(AppLanguage.storageKey) private var appLanguageRaw: String = AppLanguage.korean.rawValue

    private enum FeedbackHoldSlot: Hashable {
        case correctKo, correctEn, wrongKo, wrongEn
    }

    @State private var activeHoldSlot: FeedbackHoldSlot?
    @AppStorage(ItemCategory.appStorageKey) private var themeCategoriesStorage: String = ItemCategory.defaultStorageValue

    private var language: AppLanguage {
        AppLanguage(rawValue: appLanguageRaw) ?? .korean
    }

    private var selectedCategories: Set<ItemCategory> {
        ItemCategory.decodeSet(from: themeCategoriesStorage)
    }

    private let cream = Color(red: 1, green: 0.97, blue: 0.91)
    private let cardCorner: CGFloat = 28
    /// 시맨틱 색(Primary/Secondary)이 다크 모드·시트에서 배경과 겹쳐 사라지는 것을 막음
    private let textMain = Color(red: 0.12, green: 0.12, blue: 0.14)
    private let textMuted = Color(red: 0.38, green: 0.38, blue: 0.42)
    private let segmentOff = Color(red: 0.28, green: 0.28, blue: 0.32)

    var body: some View {
        ZStack {
            cream.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    headerCard

                    sectionLabel(language.sectionLanguage)
                    languageCard

                    sectionLabel(language.sectionItemCategories)
                    itemCategoriesCard

                    sectionLabel(language.sectionBgm)
                    bgmCard

                    sectionLabel(language.sectionFeedbackRecording)
                    feedbackRecordingCard

                    footerNote
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .padding(.bottom, 24)
            }
        }
        .preferredColorScheme(.light)
        .navigationTitle(language.settingsNavigationTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(cream, for: .navigationBar)
        .toolbarColorScheme(.light, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(language.settingsDone) { dismiss() }
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.orange)
            }
        }
    }

    private var headerCard: some View {
        HStack(alignment: .center, spacing: 14) {
            Text("🚌")
                .font(.system(size: 48))
                .frame(width: 64, height: 64)
                .background(
                    Circle()
                        .fill(Color.orange.opacity(0.12))
                )
            VStack(alignment: .leading, spacing: 6) {
                Text(language.settingsHeaderTitle)
                    .font(.system(size: 24, weight: .black, design: .rounded))
                    .foregroundColor(.orange)
                Text(language.settingsHeaderSubtitle)
                    .font(.subheadline)
                    .foregroundColor(textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .background(whiteCardStroke(Color.orange.opacity(0.35), lineWidth: 5))
    }

    private func toggleCategory(_ c: ItemCategory) {
        var s = ItemCategory.decodeSet(from: themeCategoriesStorage)
        if s.contains(c) {
            if s.count > 1 { s.remove(c) }
        } else {
            s.insert(c)
        }
        themeCategoriesStorage = ItemCategory.encodeSet(s)
    }

    private func categoryTitle(_ c: ItemCategory) -> String {
        switch c {
        case .fruit: return language.categoryFruit
        case .car: return language.categoryCar
        case .vegetable: return language.categoryVegetable
        }
    }

    private var itemCategoriesCard: some View {
        let titleColor = textMain
        let hintColor = textMuted
        let boxUnchecked = Color(red: 0.42, green: 0.42, blue: 0.46)

        return VStack(alignment: .leading, spacing: 10) {
            Text(language.itemCategoryHint)
                .font(.subheadline)
                .foregroundColor(hintColor)
                .fixedSize(horizontal: false, vertical: true)
            VStack(alignment: .leading, spacing: 2) {
                ForEach(ItemCategory.allCases) { cat in
                    Button {
                        toggleCategory(cat)
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: selectedCategories.contains(cat) ? "checkmark.square.fill" : "square")
                                .font(.system(size: 24))
                                .foregroundColor(selectedCategories.contains(cat) ? .orange : boxUnchecked)
                            Text(categoryTitle(cat))
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundColor(titleColor)
                            Spacer(minLength: 0)
                        }
                        .padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(20)
        .background(whiteCardStroke(Color(white: 0.82), lineWidth: 5))
    }

    private var languageCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(language == .korean ? "읽기·안내에 사용할 언어" : "Language for hints and speech")
                .font(.subheadline)
                .foregroundColor(textMuted)
            HStack(spacing: 0) {
                ForEach(AppLanguage.allCases) { lang in
                    Button {
                        appLanguageRaw = lang.rawValue
                    } label: {
                        Text(lang == .korean ? lang.languageKoreanLabel : lang.languageEnglishLabel)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(language == lang ? .white : segmentOff)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(language == lang ? Color.orange : Color.clear)
                            .cornerRadius(20)
                    }
                }
            }
            .padding(4)
            .background(Color(white: 0.91))
            .cornerRadius(24)
        }
        .padding(20)
        .background(whiteCardStroke(Color(white: 0.82), lineWidth: 5))
    }

    private var feedbackRecordingCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(language.feedbackRecordingIntro)
                .font(.subheadline)
                .foregroundColor(textMuted)
                .fixedSize(horizontal: false, vertical: true)

            feedbackRecordingRow(
                label: language.feedbackCorrectKorean,
                kind: .correct,
                lang: .korean,
                slot: .correctKo
            )
            feedbackRecordingRow(
                label: language.feedbackCorrectEnglish,
                kind: .correct,
                lang: .english,
                slot: .correctEn
            )
            feedbackRecordingRow(
                label: language.feedbackWrongKorean,
                kind: .wrong,
                lang: .korean,
                slot: .wrongKo
            )
            feedbackRecordingRow(
                label: language.feedbackWrongEnglish,
                kind: .wrong,
                lang: .english,
                slot: .wrongEn
            )
        }
        .padding(20)
        .background(whiteCardStroke(Color(white: 0.82), lineWidth: 5))
    }

    private func feedbackRecordingRow(
        label: String,
        kind: AnswerFeedbackKind,
        lang: AppLanguage,
        slot: FeedbackHoldSlot
    ) -> some View {
        let hasFile = feedbackRecorder.hasRecording(kind: kind, language: lang)
        let isThisSlot = activeHoldSlot == slot
        let showingRecord = feedbackRecorder.isRecording && isThisSlot

        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(label)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(textMain)
                    Text(hasFile ? language.feedbackRecordedBadge : language.feedbackNotRecordedBadge)
                        .font(.caption)
                        .foregroundColor(hasFile ? Color.green.opacity(0.9) : textMuted)
                }
                Spacer(minLength: 8)
            }

            if hasFile {
                HStack(spacing: 10) {
                    Button {
                        guard let url = try? AnswerFeedbackRecorder.savedFileURL(kind: kind, language: lang) else { return }
                        _ = audio.playRecordedFeedback(url: url)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "play.circle.fill")
                            Text(language.feedbackPlayRecording)
                        }
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .background(Color.orange)
                        .cornerRadius(14)
                    }
                    .buttonStyle(.plain)
                    .disabled(feedbackRecorder.isRecording)

                    Button {
                        audio.stopFeedbackClip()
                        feedbackRecorder.deleteRecording(kind: kind, language: lang)
                    } label: {
                        Text(language.feedbackBackToTTS)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(textMain)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 11)
                            .background(Color(white: 0.93))
                            .cornerRadius(14)
                            .overlay(
                                RoundedRectangle(cornerRadius: 14)
                                    .stroke(Color.orange.opacity(0.5), lineWidth: 1.5)
                            )
                    }
                    .buttonStyle(.plain)
                    .disabled(feedbackRecorder.isRecording)
                    .accessibilityHint(language.feedbackDeleteRecording)
                }
            }

            Text(showingRecord ? language.feedbackRecordingNow : language.feedbackRecordHoldButton)
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(showingRecord ? .white : .orange)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(showingRecord ? Color.red.opacity(0.88) : Color.orange.opacity(0.16))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Color.orange.opacity(showingRecord ? 0 : 0.45), lineWidth: 2)
                )
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { _ in
                            if activeHoldSlot == nil {
                                activeHoldSlot = slot
                                feedbackRecorder.pressBeganRecording(kind: kind, language: lang)
                            }
                        }
                        .onEnded { _ in
                            if activeHoldSlot == slot {
                                feedbackRecorder.pressEndedRecording(kind: kind, language: lang)
                                activeHoldSlot = nil
                            }
                        }
                )
        }
    }

    private var bgmCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                Image(systemName: "speaker.wave.2.fill")
                    .foregroundColor(.orange)
                    .font(.system(size: 20))
                Text(language.bgmHeadline)
                    .font(.headline)
                    .foregroundColor(textMain)
            }
            Toggle(isOn: $audio.isBgmEnabled) {
                Text(language.bgmToggleTitle)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(textMain)
            }
            .tint(.orange)
            Slider(value: $audio.bgmVolume, in: 0...1, step: 0.02)
                .tint(.orange)
                .opacity(audio.isBgmEnabled ? 1 : 0.5)
            Text(language.bgmCaption)
                .font(.caption)
                .foregroundColor(textMuted)
        }
        .padding(20)
        .background(whiteCardStroke(Color(white: 0.82), lineWidth: 5))
    }

    private var footerNote: some View {
        Text(language.settingsFooter)
            .font(.footnote)
            .foregroundColor(textMuted)
            .frame(maxWidth: .infinity, alignment: .center)
            .multilineTextAlignment(.center)
            .padding(.top, 8)
    }

    private func sectionLabel(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 14, weight: .bold))
            .foregroundColor(Color(red: 0.95, green: 0.45, blue: 0.1))
            .padding(.leading, 4)
    }

    private func whiteCardStroke(_ color: Color, lineWidth: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: cardCorner, style: .continuous)
            .fill(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: cardCorner, style: .continuous)
                    .stroke(color, lineWidth: lineWidth)
            )
            .shadow(color: Color.black.opacity(0.1), radius: 12, y: 5)
    }
}
