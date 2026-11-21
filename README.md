# KTU Student Toolkit

**A small Java CLI that puts a KTU student's ETLab data and previous-year question papers in one place.**

## What is this project?

KTU Student Toolkit is a console application for B.Tech students. Instead of switching between the ETLab website and question-paper sites, you can:

- sign in once and view your ETLab profile, timetable, attendance, subjects, faculty details and university results from the terminal,
- calculate SGPA/CGPA using the KTU 2024 grading rules,
- download previous year 2024 scheme question papers by entering a subject code,
- optionally sync your ETLab data into your own Supabase database and read from that cached copy until you sync again.

## How does it work?

When the app starts, it asks you to pick a data mode:

```text
CHOOSE HOW TO STORE DATA
1. WITH JDBC (SYNC TO SUPABASE)
2. WITHOUT JDBC (LIVE FETCH ONLY)
```

### Without JDBC

No database is needed. Every time you open an ETLab dashboard section, the app makes a fresh HTTPS request to `https://marian.etlab.in` (you can replace this url with your college url), parses the returned HTML and prints the result. This is the easiest mode for demonstrations.

### With JDBC

If your `.env` file has Supabase database settings, choosing this mode runs an initial ETLab → Supabase sync immediately. If the sync succeeds, the dashboard reads from Supabase instead of ETLab on every request until you run the sync again. If the sync fails, the app returns to the mode selection screen so you can fix the setup or choose live mode.

The sync writes to tables such as `students`, `subjects`, `teachers`, `timetable_slots`, `attendance_summary`, `attendance_daily`, `marks`, `university_results` and `sync_log`.

### Question papers

The PYQ downloader queries the [KTU Archive](https://ktu-archive.vercel.app/) Supabase catalogue using the subject code you enter, then downloads the matching PDFs into:

```text
~/Downloads/papers/<SUBJECT CODE>/
```

## Features

### ETLab Dashboard

- Profile
- Timetable slots
- Attendance summary
- Monthly present/absent grid for a chosen semester, month and year
- Subjects with teachers and attendance percentages
- Faculty details
- University result for a semester
- SGPA/CGPA calculator using the 2024 scheme

### PYQ Downloader

- Takes a subject code as input.
- Fetches papers from [KTU Archive](https://ktu-archive.vercel.app/) Supabase storage.
- Downloads all the available papers.

## Clone and run on your device

```bash
git clone 'https://github.com/rohith-pai/ktu-student-toolkit/'
cd ktu-student-toolkit
```

### Windows (PowerShell / CMD)

Compile:

```powershell
javac -d out -sourcepath src/main/java src/main/java/com/ktutoolkit/Main.java
```

Run:

```powershell
java -cp "out;lib/*" com.ktutoolkit.Main
```

### Linux / macOS

Compile:

```bash
javac -d out -sourcepath src/main/java src/main/java/com/ktutoolkit/Main.java
```

Run:

```bash
java -cp 'out:lib/*' com.ktutoolkit.Main
```

## Configuration with `.env`

Copy the example file and fill in your values:

```bash
cp .env.example .env
```

Then edit `.env`:

```text
DB_URL=jdbc:postgresql://aws-0-YOUR_REGION.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0
DB_USER=postgres.YOUR_PROJECT_REF
DB_PASSWORD=your_database_password

ETLAB_USERNAME=your_etlab_username
ETLAB_PASSWORD=your_etlab_password
ETLAB_SESSION_TOKEN=
```

## Optional Supabase setup

If you want JDBC sync:

1. Create an account at <https://supabase.com>.
2. Create a new project and save its database password.
3. Open **SQL Editor → New query**, paste the contents of `db/schema.sql`, and run it.
4. Go to **Project Settings → Database → Connection string → Transaction pooler**.
5. Copy the values into `.env`:
   - `DB_URL` is the JDBC form of the connection string, ending in `?sslmode=require&prepareThreshold=0`.
   - `DB_USER` is `postgres.<PROJECT-REF>`.
   - `DB_PASSWORD` is the project database password.
6. Choose **WITH JDBC** at startup. The initial sync will run automatically.

## Project layout

```text
src/main/java/com/ktutoolkit/
├── Main.java                 startup, data-mode chooser and main menu
├── core/                     shared CLI helpers and .env loader
├── db/                       JDBC connection, sync repository and cache check
├── etlab/                    ETLab HTTP client, dashboard and HTML parsers
├── etlab/model/              simple data classes for ETLab records
├── exceptions/               custom checked exceptions
├── pyq/                      Supabase paper repository and download tool
├── pyq/model/                Paper model
└── settings/                 AppConfig and Settings screen
db/schema.sql                 optional Supabase table definitions
lib/                          PostgreSQL JDBC driver
LICENSE                       MIT License
```

## License

[MIT LICENSE](LICENSE).