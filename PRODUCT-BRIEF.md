# Product Brief: Steady

## Product idea

Steady is an Android mobile app that helps people keep a few personally important routines going and protect the time they set aside for them. It turns vague intentions into small actions attached to reliable cues, then offers an opt-in focus session that temporarily blocks selected distracting apps and makes restarting after a miss feel ordinary and easy.

**Product thesis:** people who struggle with consistency need a system that reduces the effort of starting and recovering, rather than a system that asks them to try harder.

## Who it is for

Someone who often starts routines with high ambition, misses a day or two, feels behind, and abandons the routine. They may have a busy or changing schedule, low energy, or several competing goals. They want to trust themselves again without turning their life into a tracking project.

This is a working audience hypothesis. Before building beyond a prototype, speak with people who match it and learn which routines they have tried, where the routine breaks, and what they do after a miss.

## The problem

Most habit tools make recording a completed action easy, but still leave the hard parts to the user: choosing a realistic action, remembering when to do it, fitting it into a disrupted day, and coming back after a miss. Long streaks can also make one missed day feel like failure.

## The system: make, cue, protect, do, recover, adjust

1. **Make it small.** The user chooses one routine and defines a minimum version that takes two minutes or less, a normal version, and an optional extra. The minimum version counts as a real success.
2. **Give it a cue.** The user chooses a specific existing event and context, such as “after I make coffee, at the kitchen table.” If the cue is unreliable, they can choose a time window instead.
3. **Prepare for disruption.** The user writes a fallback for a predictable obstacle: “If I get home late, I’ll do the two-minute version before bed.” The app helps keep the fallback easy to act on.
4. **Protect the time.** The user can start a timed focus session, choose distracting apps to block, and grant Android's accessibility permission themselves. The app returns them to the home screen if they open a selected app while the session is active. The user can end a session at any time.
5. **Make starting simple.** At the chosen cue, the user sees one clear action and can mark the minimum, normal, or extra version done. They can also snooze or skip without losing progress.
6. **Recover without penalty.** If a planned action is missed, the next prompt offers a restart: do the minimum version at the next cue, change the cue, or pause the routine. There is no broken streak or backlog to repay.
7. **Adjust from experience.** A short weekly review shows what happened and asks one question: “What should we make easier next week?” The user can shrink the action, move the cue, change the fallback, or keep the plan.

## First version

Build a native Android app with Kotlin and Jetpack Compose. The first prototype implements custom user routines and the focus-locker loop.

- Routine builder: name a routine, add an optional intention, choose its local start time and repeat days, set a 5–180 minute duration, and choose the apps that routine pauses.
- Automatic schedules: schedule each enabled routine independently and restore schedules after reboot, app update, and phone time-zone changes.
- Permission guide: explain the Accessibility Service in plain language and open Android Settings only when the user asks. The service is off until the user enables it there.
- Active session: show remaining time, selected apps, and an obvious “End focus” action in Steady and in an optional ongoing notification.
- App blocking: when the user opens a selected app during an active session, return them to Home. Do not interfere with emergency calling, settings, or other apps.
- Session history: keep the latest 20 completed and manually ended sessions locally.
- Privacy and persistence: process app-switch events on-device; store routines, selections, history, and active-session timing locally; never upload app usage data.
- Next product phase: add routine cues linked to daily events, a minimum action and fallback for missed sessions, a weekly review, and export/delete controls.

## Example

**Intention:** “I want to read more.”

- Minimum: read one page.
- Normal: read for ten minutes.
- Extra: keep reading if it feels good.
- Cue: after brushing teeth, sit in bed with the book already on the pillow.
- Fallback: if I’m too tired to read, read one paragraph or move the cue to lunch tomorrow.
- Recovery: after a missed evening, the app offers one page tonight or a cue change. It does not ask the user to make up the missed reading.

## Product principles

- Progress means returning, not protecting a perfect streak.
- A tiny completion is meaningful; it is not labeled as inferior.
- Users choose the routines, cues, and reminder frequency.
- The app suggests adjustments, but the user stays in control.
- Misses are information about the plan, not a character judgment.
- The product should remain useful with one routine and a few minutes of attention per week.

## What not to build in the first version

- Public streaks, rankings, or social comparison.
- A large catalog of prebuilt routines that distracts from making one plan workable.
- Punitive notifications, guilt language, or “catch up” debt.
- Gamified rewards that compete with the user’s own reason for doing the routine.
- AI coaching, complex analytics, or integrations before the core loop works.

## How to know it is working

The main outcome is **intentional follow-through**: among focus sessions users start, what share finish without opening a selected distracting app, and what share of users come back to use focus again the next week?

Track alongside it:

- **Session completion:** sessions ended by the timer divided by all started sessions.
- **Return after interruption:** users who resume or complete their focus session after an attempted distraction.
- **User trust:** whether users say the app helps them protect time while staying in control.

Do not optimize for notification opens, blocked app launches, or number of sessions started. Those can rise while users feel controlled or frustrated.

## Key risks and how to learn

| Risk | How to address it |
|---|---|
| The minimum action feels too small to matter | Let the user define what counts; ask whether the minimum made starting easier and supported the larger goal. |
| Accessibility permission feels invasive | Explain exactly what Android grants, process events locally, request it only when the user enables blocking, and offer a complete app experience without it. |
| A session feels like a trap | Make session length visible, include an in-app end control, and never prevent access to Android settings, calls, or system controls. |
| Notifications become noise | Keep notifications optional and request permission only when the user enables session reminders. |
| Android background behavior differs by device | Test the accessibility service on physical devices from multiple manufacturers before broad release. |
| Blocking makes an important app unavailable | Let users edit the blocked list and end a session at any time; never block system or emergency apps. |

## Validation before full build

1. Interview 5–8 people who describe themselves as inconsistent and ask when phone distraction has interrupted a routine they wanted to keep.
2. Prototype focus setup, permission explanation, active session, and session completion.
3. Run a one-week pilot with short, user-selected focus sessions and a small blocked-app list.
4. Continue if users understand the permission, can end sessions easily, and report that sessions help them return to the action they chose. Rework the blocker if users feel trapped or if returning to Home does not help them refocus.

## Recommendation

**Proceed to a native Android prototype and small validation pilot.** The product has a clear, testable mechanism: let users choose a short distraction-free window, return to Home when they open a selected app, and keep an easy exit. The main uncertainties are Android device behavior and whether users find the permission and interruption flow helpful rather than controlling.
