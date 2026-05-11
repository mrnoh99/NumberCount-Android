//
//  ThemeCatalog.swift
//  NumberCount
//

import SwiftUI

enum ItemCategory: String, CaseIterable, Identifiable {
    case fruit
    case car
    case vegetable

    var id: String { rawValue }

    static let appStorageKey = "selectedThemeCategories"
    static let defaultStorageValue = "fruit,car,vegetable"

    static func decodeSet(from s: String) -> Set<ItemCategory> {
        let parts = s.split(separator: ",")
            .map { String($0).trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        let decoded = Set(parts.compactMap { ItemCategory(rawValue: $0) })
        if decoded.isEmpty { return Set(ItemCategory.allCases) }
        return decoded
    }

    /// 최소 한 종류는 켜진 문자열로 저장
    static func encodeSet(_ set: Set<ItemCategory>) -> String {
        let ordered = ItemCategory.allCases.filter { set.contains($0) }
        if ordered.isEmpty { return defaultStorageValue }
        return ordered.map(\.rawValue).joined(separator: ",")
    }
}

struct Theme {
    let category: ItemCategory
    let item: String
    let itemWordSingular: String
    let itemWordPlural: String
    let itemWordSingularKO: String
    let itemWordPluralKO: String
    let colors: [Color]
}

enum ThemeCatalog {
    private static let fruitThemes: [Theme] = [
        Theme(category: .fruit, item: "🍎", itemWordSingular: "apple", itemWordPlural: "apples", itemWordSingularKO: "사과", itemWordPluralKO: "사과들", colors: [.red, Color(red: 0.9, green: 0.2, blue: 0.25), .pink, Color(red: 0.85, green: 0.15, blue: 0.2)]),
        Theme(category: .fruit, item: "🍌", itemWordSingular: "banana", itemWordPlural: "bananas", itemWordSingularKO: "바나나", itemWordPluralKO: "바나나들", colors: [.yellow, Color(red: 0.96, green: 0.82, blue: 0.2), .orange, Color(red: 0.95, green: 0.75, blue: 0.15)]),
        Theme(category: .fruit, item: "🍇", itemWordSingular: "grape", itemWordPlural: "grapes", itemWordSingularKO: "포도", itemWordPluralKO: "포도들", colors: [.purple, .indigo, Color(red: 0.45, green: 0.2, blue: 0.65), Color(red: 0.55, green: 0.35, blue: 0.8)]),
        Theme(category: .fruit, item: "🍊", itemWordSingular: "orange", itemWordPlural: "oranges", itemWordSingularKO: "오렌지", itemWordPluralKO: "오렌지들", colors: [.orange, Color(red: 1, green: 0.55, blue: 0.1), .yellow, Color(red: 0.95, green: 0.5, blue: 0.1)]),
        Theme(category: .fruit, item: "🍓", itemWordSingular: "strawberry", itemWordPlural: "strawberries", itemWordSingularKO: "딸기", itemWordPluralKO: "딸기들", colors: [.red, .pink, Color(red: 0.95, green: 0.25, blue: 0.4), Color(red: 0.85, green: 0.2, blue: 0.35)]),
        Theme(category: .fruit, item: "🍑", itemWordSingular: "peach", itemWordPlural: "peaches", itemWordSingularKO: "복숭아", itemWordPluralKO: "복숭아들", colors: [Color(red: 1, green: 0.7, blue: 0.65), .orange, Color(red: 0.98, green: 0.55, blue: 0.5), .pink]),
        Theme(category: .fruit, item: "🥝", itemWordSingular: "kiwi", itemWordPlural: "kiwis", itemWordSingularKO: "키위", itemWordPluralKO: "키위들", colors: [.green, Color(red: 0.35, green: 0.65, blue: 0.25), .mint, Color(red: 0.5, green: 0.75, blue: 0.35)]),
        Theme(category: .fruit, item: "🍉", itemWordSingular: "watermelon", itemWordPlural: "watermelons", itemWordSingularKO: "수박", itemWordPluralKO: "수박들", colors: [.green, .red, Color(red: 0.2, green: 0.55, blue: 0.3), Color(red: 0.9, green: 0.15, blue: 0.2)]),
        Theme(category: .fruit, item: "🍋", itemWordSingular: "lemon", itemWordPlural: "lemons", itemWordSingularKO: "레몬", itemWordPluralKO: "레몬들", colors: [.yellow, Color(red: 0.98, green: 0.92, blue: 0.45), .mint, Color(red: 0.9, green: 0.88, blue: 0.35)]),
        Theme(category: .fruit, item: "🍒", itemWordSingular: "cherry", itemWordPlural: "cherries", itemWordSingularKO: "체리", itemWordPluralKO: "체리들", colors: [.red, Color(red: 0.75, green: 0.1, blue: 0.2), .pink, Color(red: 0.55, green: 0.1, blue: 0.25)]),
        Theme(category: .fruit, item: "🥭", itemWordSingular: "mango", itemWordPlural: "mangoes", itemWordSingularKO: "망고", itemWordPluralKO: "망고들", colors: [Color(red: 1, green: 0.65, blue: 0.2), .orange, .yellow, Color(red: 0.95, green: 0.55, blue: 0.15)]),
        Theme(category: .fruit, item: "🍐", itemWordSingular: "pear", itemWordPlural: "pears", itemWordSingularKO: "배", itemWordPluralKO: "배들", colors: [.green, Color(red: 0.75, green: 0.88, blue: 0.45), .mint, Color(red: 0.55, green: 0.75, blue: 0.35)]),
    ]

    private static let carThemes: [Theme] = [
        Theme(category: .car, item: "🚗", itemWordSingular: "car", itemWordPlural: "cars", itemWordSingularKO: "자동차", itemWordPluralKO: "자동차들", colors: [.orange, Color(red: 1, green: 0.56, blue: 0.69), Color(red: 1, green: 0.7, blue: 0.28), .red]),
        Theme(category: .car, item: "🚕", itemWordSingular: "taxi", itemWordPlural: "taxis", itemWordSingularKO: "택시", itemWordPluralKO: "택시들", colors: [Color(red: 0.96, green: 0.77, blue: 0.19), .orange, Color(red: 0.9, green: 0.63, blue: 0), Color(red: 0.96, green: 0.65, blue: 0.14)]),
        Theme(category: .car, item: "🚙", itemWordSingular: "SUV", itemWordPlural: "SUVs", itemWordSingularKO: "에스유브이", itemWordPluralKO: "에스유브이들", colors: [.red, Color(red: 0.91, green: 0.12, blue: 0.55), Color(red: 1, green: 0.25, blue: 0.51), Color(red: 0.78, green: 0.08, blue: 0.52)]),
        Theme(category: .car, item: "🏎️", itemWordSingular: "race car", itemWordPlural: "race cars", itemWordSingularKO: "레이싱카", itemWordPluralKO: "레이싱카들", colors: [.teal, Color(red: 0.29, green: 0.56, blue: 0.85), .purple, Color(red: 0, green: 0.54, blue: 0.48)]),
        Theme(category: .car, item: "🚓", itemWordSingular: "police car", itemWordPlural: "police cars", itemWordSingularKO: "경찰차", itemWordPluralKO: "경찰차들", colors: [.blue, .indigo, .teal, Color(red: 0.15, green: 0.35, blue: 0.72)]),
        Theme(category: .car, item: "🚑", itemWordSingular: "ambulance", itemWordPlural: "ambulances", itemWordSingularKO: "구급차", itemWordPluralKO: "구급차들", colors: [.red, .orange, Color(red: 0.85, green: 0.15, blue: 0.25), .pink]),
        Theme(category: .car, item: "🚒", itemWordSingular: "fire truck", itemWordPlural: "fire trucks", itemWordSingularKO: "소방차", itemWordPluralKO: "소방차들", colors: [.red, .orange, .yellow, Color(red: 0.78, green: 0.1, blue: 0.1)]),
        Theme(category: .car, item: "🚌", itemWordSingular: "bus", itemWordPlural: "buses", itemWordSingularKO: "버스", itemWordPluralKO: "버스들", colors: [.yellow, .orange, Color(red: 0.95, green: 0.62, blue: 0.1), .brown]),
        Theme(category: .car, item: "🚚", itemWordSingular: "truck", itemWordPlural: "trucks", itemWordSingularKO: "트럭", itemWordPluralKO: "트럭들", colors: [.mint, .teal, .cyan, Color(red: 0.23, green: 0.72, blue: 0.65)]),
        Theme(category: .car, item: "🛻", itemWordSingular: "pickup truck", itemWordPlural: "pickup trucks", itemWordSingularKO: "픽업트럭", itemWordPluralKO: "픽업트럭들", colors: [.brown, .orange, .yellow, Color(red: 0.55, green: 0.32, blue: 0.12)]),
        Theme(category: .car, item: "🚜", itemWordSingular: "tractor", itemWordPlural: "tractors", itemWordSingularKO: "트랙터", itemWordPluralKO: "트랙터들", colors: [.green, .mint, .yellow, Color(red: 0.29, green: 0.68, blue: 0.2)]),
        Theme(category: .car, item: "🛺", itemWordSingular: "rickshaw", itemWordPlural: "rickshaws", itemWordSingularKO: "인력거", itemWordPluralKO: "인력거들", colors: [.purple, .pink, .orange, Color(red: 0.6, green: 0.25, blue: 0.7)]),
    ]

    private static let vegetableThemes: [Theme] = [
        Theme(category: .vegetable, item: "🥕", itemWordSingular: "carrot", itemWordPlural: "carrots", itemWordSingularKO: "당근", itemWordPluralKO: "당근들", colors: [.orange, Color(red: 1, green: 0.55, blue: 0.15), .yellow, Color(red: 0.95, green: 0.45, blue: 0.1)]),
        Theme(category: .vegetable, item: "🥦", itemWordSingular: "broccoli", itemWordPlural: "broccolis", itemWordSingularKO: "브로콜리", itemWordPluralKO: "브로콜리들", colors: [.green, Color(red: 0.2, green: 0.55, blue: 0.28), .mint, Color(red: 0.35, green: 0.65, blue: 0.35)]),
        Theme(category: .vegetable, item: "🍅", itemWordSingular: "tomato", itemWordPlural: "tomatoes", itemWordSingularKO: "토마토", itemWordPluralKO: "토마토들", colors: [.red, Color(red: 0.9, green: 0.2, blue: 0.22), .orange, .pink]),
        Theme(category: .vegetable, item: "🥒", itemWordSingular: "cucumber", itemWordPlural: "cucumbers", itemWordSingularKO: "오이", itemWordPluralKO: "오이들", colors: [.green, Color(red: 0.4, green: 0.72, blue: 0.45), .mint, Color(red: 0.25, green: 0.55, blue: 0.35)]),
        Theme(category: .vegetable, item: "🌽", itemWordSingular: "corn", itemWordPlural: "corns", itemWordSingularKO: "옥수수", itemWordPluralKO: "옥수수들", colors: [.yellow, Color(red: 0.95, green: 0.8, blue: 0.25), .orange, Color(red: 0.85, green: 0.65, blue: 0.15)]),
        Theme(category: .vegetable, item: "🥬", itemWordSingular: "lettuce", itemWordPlural: "lettuces", itemWordSingularKO: "상추", itemWordPluralKO: "상추들", colors: [.green, Color(red: 0.45, green: 0.78, blue: 0.42), .mint, Color(red: 0.3, green: 0.6, blue: 0.32)]),
        Theme(category: .vegetable, item: "🧅", itemWordSingular: "onion", itemWordPlural: "onions", itemWordSingularKO: "양파", itemWordPluralKO: "양파들", colors: [Color(red: 0.85, green: 0.75, blue: 0.55), Color(red: 0.7, green: 0.55, blue: 0.85), .purple, Color(red: 0.95, green: 0.9, blue: 0.75)]),
        Theme(category: .vegetable, item: "🥔", itemWordSingular: "potato", itemWordPlural: "potatoes", itemWordSingularKO: "감자", itemWordPluralKO: "감자들", colors: [.brown, Color(red: 0.75, green: 0.55, blue: 0.35), Color(red: 0.9, green: 0.82, blue: 0.65), .orange]),
        Theme(category: .vegetable, item: "🍆", itemWordSingular: "eggplant", itemWordPlural: "eggplants", itemWordSingularKO: "가지", itemWordPluralKO: "가지들", colors: [.purple, .indigo, Color(red: 0.45, green: 0.2, blue: 0.55), Color(red: 0.35, green: 0.15, blue: 0.45)]),
        Theme(category: .vegetable, item: "🫑", itemWordSingular: "bell pepper", itemWordPlural: "bell peppers", itemWordSingularKO: "파프리카", itemWordPluralKO: "파프리카들", colors: [.green, .red, .yellow, .orange]),
        Theme(category: .vegetable, item: "🧄", itemWordSingular: "garlic", itemWordPlural: "garlics", itemWordSingularKO: "마늘", itemWordPluralKO: "마늘들", colors: [Color(red: 0.95, green: 0.92, blue: 0.85), Color(red: 0.88, green: 0.85, blue: 0.78), .gray, Color(red: 0.75, green: 0.72, blue: 0.68)]),
        Theme(category: .vegetable, item: "🫚", itemWordSingular: "ginger", itemWordPlural: "gingers", itemWordSingularKO: "생강", itemWordPluralKO: "생강들", colors: [Color(red: 0.95, green: 0.82, blue: 0.55), Color(red: 0.88, green: 0.72, blue: 0.45), .orange, Color(red: 0.75, green: 0.6, blue: 0.35)]),
    ]

    static let all: [Theme] = fruitThemes + carThemes + vegetableThemes

    static var defaultPool: [Theme] {
        pool(for: Set(ItemCategory.allCases))
    }

    static func pool(for categories: Set<ItemCategory>) -> [Theme] {
        let filtered = all.filter { categories.contains($0.category) }
        return filtered.isEmpty ? all : filtered
    }
}
