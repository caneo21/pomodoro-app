package com.pomodoro.timer.ui.screens.onboarding

data class OnboardingPage(val title: String, val body: String)

val onboardingPages = listOf(
    OnboardingPage(
        title = "뽀모도로 타이머에 오신 것을 환영합니다",
        body = "집중력을 높이고 번아웃을 막아주는 뽀모도로 타이머와 함께 시작해볼까요?"
    ),
    OnboardingPage(
        title = "뽀모도로 기법이란?",
        body = "25분 집중하고 5분 쉬는 것을 반복하는 시간 관리법이에요. 짧은 집중과 휴식을 번갈아가며 몰입도를 유지할 수 있어요."
    ),
    OnboardingPage(
        title = "집중 · 휴식 · 긴 휴식",
        body = "뽀모도로 4회마다 긴 휴식이 주어져요. 진행 표시 색상으로 지금이 집중인지, 휴식인지, 긴 휴식인지 한눈에 알 수 있어요."
    ),
    OnboardingPage(
        title = "타이머 사용법",
        body = "시작 · 일시정지 · 리셋 버튼으로 세션을 자유롭게 조작하세요. 진행 중인 세션은 알림으로도 확인할 수 있어요."
    ),
    OnboardingPage(
        title = "나에게 맞게 설정하기",
        body = "집중/휴식 시간, 자동 시작, 알림, 화면 꺼짐 방지, 테마, 일일 목표까지 설정 화면에서 나에게 맞게 바꿀 수 있어요."
    ),
    OnboardingPage(
        title = "시작할 준비 완료!",
        body = "지금 바로 첫 뽀모도로를 시작해보세요!"
    )
)
