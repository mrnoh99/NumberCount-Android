//
//  AppLanguage.swift
//  NumberCount
//

import Foundation

enum AppLanguage: String, CaseIterable, Identifiable {
    case korean = "ko"
    case english = "en"

    var id: String { rawValue }

    static let storageKey = "appLanguage"
    static var `default`: AppLanguage { .korean }

    // MARK: - Settings screen
    var settingsNavigationTitle: String {
        switch self {
        case .korean: return "설정"
        case .english: return "Settings"
        }
    }

    var settingsDone: String {
        switch self {
        case .korean: return "완료"
        case .english: return "Done"
        }
    }

    var settingsHeaderTitle: String {
        switch self {
        case .korean: return "숫자세기"
        case .english: return "Number Count"
        }
    }

    var settingsHeaderSubtitle: String {
        switch self {
        case .korean: return "퀴즈 화면과 같은 느낌으로 옵션을 바꿀 수 있어요."
        case .english: return "Change options in the same style as the quiz screen."
        }
    }

    var sectionLanguage: String {
        switch self {
        case .korean: return "언어"
        case .english: return "Language"
        }
    }

    var languageKoreanLabel: String { "한국어" }
    var languageEnglishLabel: String { "English" }

    var sectionDifficulty: String {
        switch self {
        case .korean: return "난이도"
        case .english: return "Difficulty"
        }
    }

    var difficultyRangeHint: String {
        switch self {
        case .korean: return "나올 수 있는 숫자 범위"
        case .english: return "Number range in quizzes"
        }
    }

    var sectionQuizMode: String {
        switch self {
        case .korean: return "퀴즈 방식"
        case .english: return "Quiz type"
        }
    }

    var quizModeFormatHint: String {
        switch self {
        case .korean: return "문제 형식"
        case .english: return "Question format"
        }
    }

    var modeCountToNumber: String {
        switch self {
        case .korean: return "갯수 - 숫자"
        case .english: return "Count → Number"
        }
    }

    var modeNumberToCount: String {
        switch self {
        case .korean: return "숫자 - 갯수"
        case .english: return "Number → Count"
        }
    }

    var sectionBgm: String {
        switch self {
        case .korean: return "배경 음악"
        case .english: return "Background music"
        }
    }

    var bgmHeadline: String {
        switch self {
        case .korean: return "배경 음악만 조절해요"
        case .english: return "Adjust background music only"
        }
    }

    var bgmToggleTitle: String {
        switch self {
        case .korean: return "배경 음악 재생"
        case .english: return "Play background music"
        }
    }

    var bgmCaption: String {
        switch self {
        case .korean: return "끄면 배경 음악만 멈춰요. 정답·오답은 음성으로 안내합니다."
        case .english: return "When off, only background music stops. Spoken feedback still plays."
        }
    }

    var settingsFooter: String {
        switch self {
        case .korean: return "메인 화면에서도 난이도와 모드를 바꿀 수 있어요."
        case .english: return "You can also change difficulty and mode on the main screen."
        }
    }

    var mainSettingsButton: String {
        switch self {
        case .korean: return "설정"
        case .english: return "Settings"
        }
    }

    /// 정답 직후 읽는 말·축하 화면 문구
    var feedbackCorrectPhrase: String {
        switch self {
        case .korean: return "그래 잘했다!"
        case .english: return "That's right!"
        }
    }

    /// 오답 직후 읽는 말 (이어서 숫자 안내가 나옵니다)
    var feedbackWrongPhrase: String {
        switch self {
        case .korean: return "틀렸어요."
        case .english: return "Not quite."
        }
    }

    var sectionFeedbackRecording: String {
        switch self {
        case .korean: return "정답·오답 음성 녹음"
        case .english: return "Record answer sounds"
        }
    }

    var feedbackRecordingIntro: String {
        switch self {
        case .korean: return "버튼을 누르고 있는 동안만 녹음됩니다. 손을 떼면 앞뒤 무음이 잘리고 저장돼요. 녹음이 있으면 퀴즈에서 TTS 대신 재생됩니다."
        case .english: return "Hold the button to record. Release to trim silence and save. If saved, your clip plays instead of speech synthesis."
        }
    }

    var feedbackRecordHoldButton: String {
        switch self {
        case .korean: return "누르는 동안 녹음"
        case .english: return "Hold to record"
        }
    }

    var feedbackRecordedBadge: String {
        switch self {
        case .korean: return "저장됨"
        case .english: return "Saved"
        }
    }

    var feedbackNotRecordedBadge: String {
        switch self {
        case .korean: return "없음 (TTS)"
        case .english: return "None (TTS)"
        }
    }

    var feedbackRecordingNow: String {
        switch self {
        case .korean: return "녹음 중…"
        case .english: return "Recording…"
        }
    }

    var feedbackCorrectKorean: String {
        switch self {
        case .korean: return "정답 · 한국어 (예: 그래 잘했다!)"
        case .english: return "Correct · Korean (예: 그래 잘했다!)"
        }
    }

    var feedbackCorrectEnglish: String {
        switch self {
        case .korean: return "정답 · English (example:Good job)"
        case .english: return "Correct · English (example:Good job) "
        }
    }

    var feedbackWrongKorean: String {
        switch self {
        case .korean: return "오답 · 한국어 (예: 아닌데!)"
        case .english: return "Wrong · Korean (예: 아닌데!)"
        }
    }

    var feedbackWrongEnglish: String {
        switch self {
        case .korean: return "오답 · English (예: No!)"
        case .english: return "Wrong · English (example:No!)"
        }
    }

    var feedbackDeleteRecording: String {
        switch self {
        case .korean: return "녹음 삭제"
        case .english: return "Delete recording"
        }
    }

    var feedbackPlayRecording: String {
        switch self {
        case .korean: return "듣기"
        case .english: return "Play"
        }
    }

    /// 저장된 녹음을 지우고 퀴즈에서 다시 TTS 사용
    var feedbackBackToTTS: String {
        switch self {
        case .korean: return "TTS로 돌아가기"
        case .english: return "Back to TTS"
        }
    }

    var sectionItemCategories: String {
        switch self {
        case .korean: return "아이템 종류"
        case .english: return "Item categories"
        }
    }

    var itemCategoryHint: String {
        switch self {
        case .korean: return "퀴즈에 나올 과일·자동차·채소를 선택하세요. 한 가지 이상 켜 두어야 합니다."
        case .english: return "Choose fruits, cars, or vegetables for quizzes. At least one must stay on."
        }
    }

    var categoryFruit: String {
        switch self {
        case .korean: return "과일"
        case .english: return "Fruits"
        }
    }

    var categoryCar: String {
        switch self {
        case .korean: return "자동차"
        case .english: return "Cars"
        }
    }

    var categoryVegetable: String {
        switch self {
        case .korean: return "채소"
        case .english: return "Vegetables"
        }
    }
}
