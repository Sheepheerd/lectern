# Ideas not built yet

Things deliberately left out, kept here so they aren't lost. The first one is
the big one.

## Audio Books

Somehow integrate audio books to also be an option the user can play from. Maybe
implement the AI reading of certain books?

## The trainer

A reading coach built into Lectern: it works out how fast you actually read,
pushes that number up a little at a time, and shows you the curve.

The premise is that speed alone is a bad target. Anyone can hold 900 wpm and
take in nothing. What a trainer should optimise is the fastest pace at which
comprehension still holds — and that number is personal, changes with the
material, and moves as you practise.

**How it would work**

1. **Calibrate.** A first session reads a few hundred words at a moderate pace,
   then asks two or three plain questions about what was in them. That fixes a
   starting point instead of guessing one.
2. **Push, then check.** Each session runs a little above your current best —
   5–10% — for a set stretch, then poses a short comprehension check. Passing
   raises the ceiling; missing lowers it. This is progressive overload, the same
   shape as any training plan.
3. **Vary the material.** Track pace separately for easy prose and dense
   writing, because they aren't the same skill. The per-book speed Lectern
   already keeps is the seed of this.
4. **Drills.** Short exercises rather than whole books: a chunking drill that
   steps 1 → 2 → 3 words; a regression drill that turns the context line off; a
   fixation drill at a fixed pace with no controls at all.
5. **Show the curve.** Words read, minutes, pace over weeks, comprehension
   scores against pace. The interesting plot is comprehension vs. speed — the
   knee in that curve *is* your reading speed, and watching it move right is the
   whole reward.

**What it needs that doesn't exist yet**

- A sessions store: start, end, words, pace, book, comprehension score.
- Comprehension checks. Hand-written questions don't scale; the honest cheap
  version is self-report ("how much did you get? none / gist / most / all"),
  which is enough to drive the ceiling up or down. Generated questions would be
  better and need a model, which would mean sending text off the device —
  against the grain of the rest of the app, so it should stay optional.
- A stats screen, and the streak/goal machinery that comes with it.

**Why it isn't built yet**: it is a second app's worth of design — sessions,
scoring, charts, goals — and it only pays off once the reading itself is good.
The reading is now good.

## Stats and habit

The groundwork the trainer needs, useful on its own:

- Session summaries: words, minutes, actual average pace (which differs from
  the dial, because of pauses).
- Streaks and a daily goal — fifteen minutes a day is a real habit.
- Per-book history: when you read it, how fast, how far.
- Finish estimates from your own measured pace rather than the wpm setting.

## Platform integration

- A home-screen widget (Glance) with continue-reading and progress.
- A reading-session notification with pause — it would also stop Android
  killing the process mid-book.
- A Quick Settings tile to resume.
- Text-to-speech in sync with the flashing words, for hard material.

## Elsewhere on the shelf

- Full collections rather than flat tags — folders you can nest.
- Sync between devices. Would need a backend; the backup zip is the local
  answer for now.
- OPDS or Calibre import, for people with a library server already.
- Highlights: mark a passage while reading and export the lot.
