//
//  AnswerFeedbackRecorder.swift
//  NumberCount
//

import AVFoundation
import Combine
import Foundation

enum AnswerFeedbackKind: String {
    case correct
    case wrong
}

/// 정답·오답 짧은 코멘트 사용자 녹음 (누르는 동안만), 무음 트림 후 저장
@MainActor
final class AnswerFeedbackRecorder: NSObject, ObservableObject {
    @Published private(set) var isRecording = false
    @Published private(set) var recordingsRevision: Int = 0

    /// 녹음 세션 종료 후 BGM·재생 세션 복구
    var restorePlaybackSession: (() -> Void)?

    private var recorder: AVAudioRecorder?
    private var tempRecordingURL: URL?

    private static var recorderSettings: [String: Any] {
        [
            AVFormatIDKey: Int(kAudioFormatLinearPCM),
            AVSampleRateKey: 44_100,
            AVNumberOfChannelsKey: 1,
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsBigEndianKey: false,
            AVLinearPCMIsNonInterleaved: false
        ]
    }

    static func storageDirectory() throws -> URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let dir = base.appendingPathComponent("NumberCount", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func savedFileURL(kind: AnswerFeedbackKind, language: AppLanguage) throws -> URL {
        try storageDirectory().appendingPathComponent("feedback_\(kind.rawValue)_\(language.rawValue).caf")
    }

    func hasRecording(kind: AnswerFeedbackKind, language: AppLanguage) -> Bool {
        guard let url = try? Self.savedFileURL(kind: kind, language: language) else { return false }
        return FileManager.default.fileExists(atPath: url.path)
    }

    func pressBeganRecording(kind: AnswerFeedbackKind, language: AppLanguage) {
        guard !isRecording else { return }
        Task { @MainActor in
            await startRecordingIfPermitted(kind: kind, language: language)
        }
    }

    private func startRecordingIfPermitted(kind: AnswerFeedbackKind, language: AppLanguage) async {
        guard !isRecording else { return }
        let allowed = await AVAudioApplication.requestRecordPermission()
        guard allowed else { return }
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .mixWithOthers])
            try session.setActive(true, options: [])
            let temp = FileManager.default.temporaryDirectory
                .appendingPathComponent("feedback_\(kind.rawValue)_\(language.rawValue)_\(UUID().uuidString).caf")
            tempRecordingURL = temp
            let recorder = try AVAudioRecorder(url: temp, settings: Self.recorderSettings)
            recorder.delegate = self
            recorder.isMeteringEnabled = true
            recorder.prepareToRecord()
            guard recorder.record() else { return }
            self.recorder = recorder
            isRecording = true
        } catch {
            cleanupRecorderOnly()
            restorePlaybackSession?()
        }
    }

    func pressEndedRecording(kind: AnswerFeedbackKind, language: AppLanguage) {
        guard isRecording || tempRecordingURL != nil else {
            restorePlaybackSession?()
            return
        }
        recorder?.stop()
        recorder = nil
        isRecording = false
        guard let temp = tempRecordingURL else {
            restorePlaybackSession?()
            return
        }
        tempRecordingURL = nil
        defer {
            try? FileManager.default.removeItem(at: temp)
        }
        do {
            let dest = try Self.savedFileURL(kind: kind, language: language)
            try FeedbackAudioTrim.trimSilence(inputURL: temp, outputURL: dest)
            recordingsRevision += 1
        } catch {
            if let dest = try? Self.savedFileURL(kind: kind, language: language) {
                try? FileManager.default.removeItem(at: dest)
            }
        }
        restorePlaybackSession?()
    }

    private func cleanupRecorderOnly() {
        recorder?.stop()
        recorder = nil
        isRecording = false
        if let temp = tempRecordingURL {
            try? FileManager.default.removeItem(at: temp)
            tempRecordingURL = nil
        }
    }

    func deleteRecording(kind: AnswerFeedbackKind, language: AppLanguage) {
        guard let url = try? Self.savedFileURL(kind: kind, language: language) else { return }
        try? FileManager.default.removeItem(at: url)
        recordingsRevision += 1
    }
}

extension AnswerFeedbackRecorder: AVAudioRecorderDelegate {
    nonisolated func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) {
        Task { @MainActor [weak self] in
            self?.cleanupRecorderOnly()
            self?.restorePlaybackSession?()
        }
    }
}

// MARK: - 무음 트림 (Linear PCM)

enum FeedbackAudioTrim {
    static func trimSilence(inputURL: URL, outputURL: URL) throws {
        let input = try AVAudioFile(forReading: inputURL)
        let length = Int(input.length)
        guard length > 0 else { throw TrimError.empty }

        let format = input.processingFormat
        let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(length))!
        try input.read(into: buffer)
        let frames = Int(buffer.frameLength)
        let sampleRate = format.sampleRate
        let padFrames = max(1, Int(sampleRate * 0.045))

        var start = 0
        var end = frames - 1

        if let ch = buffer.floatChannelData?.pointee {
            let threshold: Float = 0.018
            while start < frames && abs(ch.advanced(by: start).pointee) < threshold { start += 1 }
            while end > start && abs(ch.advanced(by: end).pointee) < threshold { end -= 1 }
        } else if let ch = buffer.int16ChannelData?.pointee {
            let threshold: Int16 = 550
            while start < frames && abs(ch.advanced(by: start).pointee) < threshold { start += 1 }
            while end > start && abs(ch.advanced(by: end).pointee) < threshold { end -= 1 }
        } else {
            if FileManager.default.fileExists(atPath: outputURL.path) {
                try FileManager.default.removeItem(at: outputURL)
            }
            try FileManager.default.copyItem(at: inputURL, to: outputURL)
            return
        }

        guard end > start else { throw TrimError.noSignal }

        start = max(0, start - padFrames)
        end = min(frames - 1, end + padFrames)
        let outFrameCount = end - start + 1
        let minFrames = Int(sampleRate * 0.08)
        guard outFrameCount >= minFrames else { throw TrimError.tooShort }

        guard let outBuffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(outFrameCount)) else {
            throw TrimError.writeFailed
        }
        outBuffer.frameLength = AVAudioFrameCount(outFrameCount)

        let channels = Int(format.channelCount)
        if let inF = buffer.floatChannelData, let outF = outBuffer.floatChannelData {
            for c in 0..<channels {
                let src = inF[c].advanced(by: start)
                memcpy(outF[c], src, outFrameCount * MemoryLayout<Float>.size)
            }
        } else if let inI = buffer.int16ChannelData, let outI = outBuffer.int16ChannelData {
            for c in 0..<channels {
                let src = inI[c].advanced(by: start)
                memcpy(outI[c], src, outFrameCount * MemoryLayout<Int16>.size)
            }
        } else {
            throw TrimError.writeFailed
        }

        if FileManager.default.fileExists(atPath: outputURL.path) {
            try FileManager.default.removeItem(at: outputURL)
        }
        let outFile = try AVAudioFile(forWriting: outputURL, settings: input.fileFormat.settings)
        try outFile.write(from: outBuffer)
    }

    enum TrimError: Error {
        case empty
        case noSignal
        case tooShort
        case writeFailed
    }
}
