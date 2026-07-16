# ShadyFit 💪

A full-featured Android gym & nutrition tracker built with **Kotlin + Jetpack Compose**.


---

## Features

### 🏋️ Workout Tracker
- Create sessions with unlimited exercises, sets, reps and weights
- Live session timer
- Mark sets as completed in real time
- Edit and delete past workouts
- Volume tracking per session

### 🫀 Animated Body Figure
- Front & back anatomical diagram drawn with **Compose Canvas**
- Muscle groups light up in real time as you add exercises
- Pulsing animation on active muscles
- Legend showing which muscles are targeted


### 🥗 Nutrition / Calorie Counter
- Search food via **Open Food Facts** API
- Log meals by type: Pequeno-almoço, Almoço, Jantar, Lanche
- Adjust portion size in grams - macros recalculate automatically
- Daily calorie ring + macro progress bars
- Set personal calorie & macro goals
- Navigate between past days


### 📊 Analytics
- Total workouts & total volume lifted
- Per-session volume bar chart
- Most-performed exercises ranked
- Personal records (automatically updated when you beat a weight)


### 💾 Offline-first
- Everything stored locally with **Room** (SQLite)
- No account required
- API data is cached in memory per session

## Setup

### 1. Clone & open
```bash
git clone https://github.com/yourname/ShadyFit.git
```
Open the project in **Android Studio Ladybug** (2024.2) or newer.

### 2. Exercise API key (optional but recommended)
The app works offline with 10 built-in exercises.  
To unlock the full ExerciseDB library with animated GIFs:

1. Sign up free at [rapidapi.com](https://rapidapi.com/justin-WFnsXH_t6/api/exercisedb)
2. Copy your API key
3. Create the `local.properties` file in the project root and place the API key like so:
```kotlin
EXERCISEDB_API_KEY=YOUR_RAPIDAPI_KEY_HERE
```

### 3. Food API
Open Food Facts is **free with no key**. It automatically covers products.  
No configuration needed.

### 4. Build & run
```
./gradlew assembleDebug
```
Or press **Run ▶** in Android Studio. Minimum SDK is **26 (Android 8.0)**.

---

## Project Structure

```
app/src/main/java/com/fittrack/
├— data/
│   ├— local/                  # Room database, entities, DAOs
│   ├— remote/                 # Retrofit API services + DTOs
│   └— repository/             # Single source of truth
├— domain/model/               # Pure Kotlin data classes
├— presentation/
│   ├— theme/                  # Material 3 dark theme + colours
│   ├— components/             # BodyFigureWidget (Canvas drawing)
│   └— screens/
│       ├— home/               # Dashboard
│       ├— workout/            # Session list, create, detail
│       ├— exercise/           # Browser + search + GIF detail sheet
│       ├— nutrition/          # Calorie tracker + food search
│       └— analytics/          # Charts + personal records
├— navigation/                 # NavHost + bottom bar
└— di/                         # Hilt modules (DB, Network)
```

---

## Muscle Group Colour Coding

| Colour | Muscle |
|--------|--------|
| 🔴 Red | Chest |
| 🔵 Blue | Back / Lats |
| 🟡 Yellow | Shoulders |
| 🟣 Purple | Arms (Biceps/Triceps) |
| 🟠 Orange | Core / Abs |
| 🟢 Green | Legs (Quads/Hamstrings) |
| 🩷 Pink | Glutes |
| 🩵 Cyan | Calves |

---

## Licence
MIT - use freely, credit appreciated.
