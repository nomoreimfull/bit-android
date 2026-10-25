# Chat-bubble research and edge extension (Android port 1.2)

## What Android and Messenger do

Google and Facebook Messenger jointly described Messenger’s adoption of Android’s conversation Bubble API in 2020: [Messenger and Conversations](https://android-developers.googleblog.com/2020/06/messenger-and-conversations.html). The Messenger team explicitly says its earlier implementation used SYSTEM_ALERT_WINDOW, then describes migration to system Bubbles. This is historical evidence for Messenger integration, not a claim about every current Messenger build or its proprietary implementation. Current Meta help uses the name [Android bubbles on Messenger](https://www.facebook.com/help/messenger-app/1611232179138526); that help page was cookie-gated during research.

[Android’s notification-bubble documentation](https://developer.android.com/develop/ui/compose/notifications/bubbles) describes system-managed floating conversation UI. Apps supply bubble metadata, icons, activities and conversation notifications. The system controls presentation and lifecycle. This differs from Bit’s continuously animated, directly tappable 3D overlay; changing Bit into a conversation notification would change its interaction model.

The public [AOSP BubblePositioner implementation](https://android.googlesource.com/platform/frameworks/base/+/e8f0ed202707f8f1fc1890a184915a3e98879452/libs/WindowManager/Shell/src/com/android/wm/shell/bubbles/BubblePositioner.java) exposes a concrete off-screen allowance (`mBubbleOffscreenAmount`). Its allowable collapsed-stack region extends left of the usable rectangle by that amount and extends the right limit by the same amount minus the bubble width. Top/bottom limits separately account for padding, keyboard and stack height. Thus a bubble can rest partly off the sides rather than clamping its entire rectangle inside. This is publicly inspectable Android behavior, not reverse engineering of Messenger’s private code.

For app-owned overlays, the supported [FLAG_LAYOUT_NO_LIMITS](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_LAYOUT_NO_LIMITS) permits the window to extend outside the screen. `TYPE_APPLICATION_OVERLAY` remains below critical system surfaces, and Android may adjust its position, size or visibility. This flag does not bypass overlay permission, system bars, locks or protected surfaces.

## Bit implementation

**Settings → Geometry → Edge extension: 0–50%.** Default 0 retains the previous full-window clamp. Increase the setting, then drag Bit toward an edge. The percentage measures the invisible square overlay window’s width/height, not the animated silhouette. It applies to all four edges; this is an experimental adaptation rather than an exact copy of Android’s side-only bubble positioning.

For a window of side `S`, extension `P` and usable screen dimension `D`, the allowable top-left coordinate relative to that usable rectangle is:

```
overhang = min(round(S × P / 100), floor(S / 2))
minimum  = -overhang
maximum  = D - S + overhang
```

The maximum half-window limit keeps Bit’s center inside the usable rectangle, so it cannot be dragged wholly out of view. At a side, up to half the square can extend beyond the boundary; at a corner, up to three quarters of its square area may be outside. Animated response shapes can be clipped, deliberately, when parked at an edge. The visible portion remains touchable wherever Android delivers touches. Android’s back/navigation edge gestures are not overridden.

`FLAG_LAYOUT_NO_LIMITS` is enabled only when the setting is nonzero. The window remains small and uses NOT_FOCUSABLE/NOT_TOUCH_MODAL, so touches outside it continue to pass through. No full-screen touch layer, accessibility service, private window type or system-gesture exclusion was added.

Changes apply immediately. Increasing the allowance preserves the current position and expands the drag range; it does not pull Bit sideways or add automatic snapping. Reducing it immediately clamps an outlying position inward. Size changes preserve the center where permitted, then clamp to the selected extension. The allowance is persisted locally.

Off-screen positions are stored as normalized center coordinates within the usable screen. Old saved positions migrate without moving at the current size. Relative center position survives service restart and display rotation/resize, subject to the active clamp. Reset position clears the saved centers and restores a visible default location whether Bit is running or stopped; it retains your chosen extension setting.

Android/OEM handling of negative overlay coordinates has not been exercised on a connected device. ADB currently lists no devices. The APK builds and signs successfully, and host tests check the placement model, persistence, rotation, resizing and recovery. A real-device check is needed to confirm the platform allows the requested overhang on the user’s phone.
