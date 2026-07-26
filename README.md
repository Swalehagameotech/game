# Teen Patti Production-Grade Platform Monorepo

Production-grade Teen Patti gaming platform architecture featuring a Spring Boot 3 (Java 21) backend, a React 19 + Vite frontend, and a MongoDB single-node replica set infrastructure.

---

## 📁 Monorepo Layout

```
.
├── backend/                        # Spring Boot 3 Maven Application (Java 21)
│   ├── src/main/java/com/teenpatti/platform/
│   │   ├── config/                # App config & Mongo index initialization runner
│   │   ├── common/
│   │   │   ├── exception/         # GlobalExceptionHandler
│   │   │   └── response/          # Standard ApiResponse & ErrorResponse wrappers
│   │   ├── user/                  # User, KycDetails, FriendRelationship documents & enums
│   │   ├── wallet/                # Wallet document (balancePaise, optimistic locking)
│   │   ├── transaction/           # LedgerEntry document (APPEND-ONLY financial audit)
│   │   ├── table/                 # Table document (game room state & player seats)
│   │   ├── game/                  # MatchHistory document (APPEND-ONLY hand audit) & HandSummary
│   │   ├── notification/          # Notification document & NotificationType enum
│   │   ├── admin/                 # AdminActionLog document (APPEND-ONLY admin audit)
│   │   ├── auth/                  # [Phase 3] Auth module placeholder
│   │   ├── lobby/                 # [Phase 8] Lobby module placeholder
│   │   ├── websocket/             # [Phase 11] Real-time messaging placeholder
│   │   ├── leaderboard/           # [Phase 15] Leaderboard placeholder
│   │   └── PlatformApplication.java # Spring Boot entrypoint
│   ├── src/main/resources/
│   │   └── application.properties # Configuration defaults & env variable mappings
│   ├── mvnw & mvnw.cmd            # Maven wrapper binaries
│   └── pom.xml                    # Backend dependencies declaration
│
├── frontend/                       # React 19 + Vite Frontend Application
│   ├── src/
│   │   ├── app/
│   │   │   ├── store.js            # Redux Toolkit store (empty root reducer)
│   │   │   └── router.jsx          # React Router setup with placeholder route
│   │   ├── features/               # Feature-based module folders (auth, wallet, etc.)
│   │   ├── shared/
│   │   │   ├── api/axiosClient.js  # Pre-configured Axios instance
│   │   │   ├── components/         # Shared UI components
│   │   │   └── hooks/              # Shared React hooks
│   │   ├── styles/index.css        # Tailwind CSS imports
│   │   ├── App.jsx                 # Styled placeholder component
│   │   └── main.jsx                # Application root entrypoint
│   ├── package.json                # React 19 & UI dependencies
│   ├── vite.config.js              # Vite config (@ path alias)
│   ├── tailwind.config.js          # Tailwind CSS theme configuration
│   ├── postcss.config.js           # PostCSS configuration
│   └── .env.example                # Frontend environment template
│
├── docker-compose.yml              # Single-node MongoDB Replica Set container configuration
├── README.md                       # Monorepo setup documentation
└── .gitignore                      # Environment, build, and node_modules exclusions
```

---

## 🗄 Database Schema & Domain Document Models

All monetary values across the platform are stored strictly as `long` quantities in **paise** (e.g. ₹100.00 = `10000` paise). Double or floating-point values are never used for money calculations.

### Collections Overview

| Collection Name | Document Class | Owning Package | Key Indexes | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `users` | `User` | `user/` | `email` (unique), `phoneNumber` (unique) | PII fields (PAN) in `KycDetails` require encryption-at-rest prior to Phase 5 KYC storage. |
| `wallets` | `Wallet` | `wallet/` | `userId` (unique) | Enforces 1 wallet per user. Uses `@Version` for optimistic locking. |
| `ledger_entries` | `LedgerEntry` | `transaction/` | `userId`, `referenceId`, `(userId, createdAt)` compound | **APPEND-ONLY BY DESIGN**. No updates/deletes permitted. |
| `tables` | `Table` | `table/` | — | Table room config & seated player IDs. Hand state is transient/in-memory. |
| `match_histories` | `MatchHistory` | `game/` | `tableId` | **APPEND-ONLY BY DESIGN**. Written once per completed hand. |
| `friend_relationships` | `FriendRelationship` | `user/` | `userId`, `friendUserId`, `(userId, friendUserId)` unique compound | Friend requests & block status. |
| `notifications` | `Notification` | `notification/` | `userId` | System & user alert records. |
| `admin_action_logs` | `AdminActionLog` | `admin/` | `adminUserId`, `targetUserId` | **APPEND-ONLY BY DESIGN**. Comprehensive admin operation audit log. |

---

## 🚀 Getting Started

### 1. Start MongoDB (Single-Node Replica Set)

Launch the MongoDB container via Docker Compose:

```bash
docker-compose up -d
```

#### 2. Initialize MongoDB Replica Set (`rs.initiate()`)

> **Note**: This step is required **only once** on initial container setup to enable Multi-Document ACID Transactions.

Execute the initiation script inside the running container:

```bash
docker exec -it teenpatti-mongo mongosh --eval "rs.initiate({_id: 'rs0', members: [{_id: 0, host: 'localhost:27017'}]})"
```

Confirm initiation status:

```bash
docker exec -it teenpatti-mongo mongosh --eval "rs.status().ok"
```
*(Should output `1`)*

---

### 3. Start the Backend Application

Navigate to the `/backend` folder and run via the Maven wrapper:

```bash
cd backend
./mvnw spring-boot:run
```

On application startup, `MongoConfig` will verify MongoDB connectivity, auto-create all 8 collections, and initialize all defined indexes.

---

### 4. Start the Frontend Application

Navigate to the `/frontend` folder, install dependencies, and launch Vite dev server:

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.
