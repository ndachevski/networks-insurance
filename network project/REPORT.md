# Software Development Report: Multi-Player Tic-Tac-Toe Network Game

## 1. Introduction

This report documents the complete software development process for a client–server, multi-player Tic-Tac-Toe game over TCP/IP. The system supports user registration, authentication, matchmaking, real-time gameplay, statistics, leaderboards, and rematch functionality. The documentation covers analysis, design, development, implementation, testing, the communication protocol, architecture, class-level descriptions, assumptions, and an evaluation including limitations and lessons learned.

---

## 2. Analysis

### 2.1 Requirements

- **Functional:**
  - User registration (username, password, name, email) and login.
  - List of online players and ability to challenge a selected player.
  - Full Tic-Tac-Toe game logic (3×3 board, turn-taking, win/draw detection).
  - Real-time board updates and game result (WIN/LOSS/DRAW).
  - Persistent user statistics (wins, losses, draws) and a leaderboard.
  - Rematch after a game and handling of opponent disconnect or leave.
  - Two client interfaces: command-line (CLI) and graphical (GUI).

- **Non-functional:**
  - Reliable, ordered message delivery over TCP.
  - Support for multiple concurrent clients and games.
  - Basic security: hashed passwords, input validation, rate limiting on login.
  - Thread-safe server state for concurrency.

### 2.2 Scope

- **In scope:** TCP-based client–server architecture, text-based protocol, file-based user persistence, single-game-type (Tic-Tac-Toe), local or LAN deployment.
- **Out of scope:** Web clients, SSL/TLS, database backend, other games, chat, friends lists, matchmaking algorithms.

---

## 3. Design

### 3.1 High-Level Architecture

The system uses a **centralised client–server** model:

- **Server:** Single Java process; accepts TCP connections on a fixed port (12345), spawns one `ClientHandler` thread per client, and coordinates all game sessions and user state.
- **Clients:** Two variants sharing the same protocol:
  - **CLI client:** `GameClient` + `GameUI` + `ServerListener` (console I/O).
  - **GUI client:** `GameClientGUI` + `ServerListenerGUI` (Swing).

Communication is **request–response and server-push**: clients send requests (e.g. LOGIN, MOVE); the server responds and may push updates (e.g. PLAYERS_LIST, UPDATE, RESULT) to one or more clients.

### 3.2 Block Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              GAME SERVER                                     │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────────────────────┐   │
│  │ ServerSocket │───▶│ ClientHandler│◀──▶│         GameServer            │   │
│  │  (port      │    │ (per client) │    │ - clients (username→handler)  │   │
│  │  12345)     │    │ - auth       │    │ - games (gameId→GameSession)  │   │
│  └──────────────┘    │ - msg router│    │ - pendingChallenges           │   │
│                      └──────┬───────┘    │ - pendingRematches            │   │
│                             │            │ - lastOpponents               │   │
│                             ▼            └──────────────┬───────────────┘   │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌───────────┐  │
│  │ UserManager  │    │ GameSession  │    │ RateLimiter  │    │ Protocol  │  │
│  │ - users.txt  │    │ - 3×3 board  │    │ - lockout    │    │ - parse   │  │
│  │ - online     │    │ - win/draw   │    │ - attempts   │    │ - create  │  │
│  └──────────────┘    └──────────────┘    └──────────────┘    └───────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                          TCP (JSON-like messages)
                                        │
        ┌───────────────────────────────┼───────────────────────────────┐
        ▼                               ▼                               ▼
┌───────────────┐             ┌───────────────┐             ┌───────────────┐
│ GameClientGUI │             │   GameClient  │             │   GameClient  │
│ +ServerListen │             │   + GameUI    │             │   + GameUI    │
│    erGUI      │             │ +ServerListen │             │ +ServerListen │
│   (Swing)     │             │     er        │             │     er        │
└───────────────┘             └───────────────┘             └───────────────┘
```

### 3.3 Main Design Decisions

| Decision | Rationale |
|----------|-----------|
| **TCP** | Reliable, ordered delivery; no need for application-level retransmission. |
| **JSON-like text protocol** | Human-readable, easy to debug; simple to parse without external libraries. |
| **One thread per client (ClientHandler)** | Straightforward concurrency model; each client’s I/O is isolated. |
| **Server as single source of truth** | All game state and user data live on the server; clients are thin. |
| **File-based user storage (users.txt)** | Simplifies deployment; no database setup. Trade-off: scalability and robustness. |
| **Custom Protocol parser** | Avoids third-party JSON dependencies; keeps the project self-contained. |
| **Rate limiting per client+username** | Mitigates brute-force login attempts. |
| **Password hashing (SHA-256 + salt)** | Avoids storing plaintext passwords; `SecurityUtils` supports plaintext migration. |
| **LEAVE_GAME on window close (GUI)** | Cleanly ends the game and updates stats when a user closes the game window. |

### 3.4 Low-Level System Design

The low-level design refines the two-tier client–server architecture into concrete components, data structures, threading, and interfaces. The **client** is responsible for UI/UX, information display, and user-request transmission; the **server** processes and responds to requests, stores data, and manages resources. The server is multi-threaded: each connected client is served by a **ClientHandler** thread, enabling concurrent access. **Game sessions** are implemented as passive objects (`GameSession`); concurrent matches are supported because each match is a separate instance and different `ClientHandler` threads operate on different games when processing moves.

---

#### 3.4.1 Server-Side Component Decomposition

The server is decomposed into the following modules and their responsibilities:

| Component | Type | Responsibility | Dependencies |
|-----------|------|----------------|--------------|
| **Server** | Entry-point class | Creates `GameServer` and calls `start()` | GameServer |
| **GameServer** | Core orchestrator | Accept loop; holds `ServerSocket` and shared maps; dispatches to `ClientHandler`, `UserManager`, `GameSession`; implements `addClient`, `removeClient`, `broadcastPlayerList`, `sendChallenge`, `handleChallengeResponse`, `startGame`, `processMove`, `handleLeaveGame`, `sendRematchRequest`, `handleRematchResponse`, `sendLeaderboard`, `sendToPlayer`, `findGameByPlayer`, `getLastOpponent` | UserManager, RateLimiter, ClientHandler, GameSession, Protocol |
| **ClientHandler** | Thread (extends `Thread`) | One instance per TCP connection; blocks on `BufferedReader.readLine()`; on each line: `Protocol.parseMessage` → switch on `type` → call `handleRegister`, `handleLogin`, `handleListPlayers`, `handleChallenge`, `handleChallengeResponse`, `handleMove`, `handleLogout`, `handleRematchRequest`, `handleRematchResponse`, `handleLeaderboard`, `handleLeaveGame`; delegates to `UserManager` and `GameServer`; on disconnect/LOGOUT runs `cleanup()` | Socket, UserManager, GameServer, RateLimiter, Protocol, SecurityUtils |
| **GameSession** | Passive domain object | Encapsulates one Tic-Tac-Toe match: `gameId`, `player1`, `player2`, `board[3][3]`, `currentPlayer`, `winner`, `gameOver`, `moveCount`; `makeMove(player,x,y)` (validates turn and cell, sets X/O, `checkWin`, draw, switch turn); `getBoardMap()`, `getResultFor(player)` | — |
| **UserManager** | Service | Load/save `users.txt`; in-memory `users` and `onlineUsers`; `register`, `login`, `isOnline`, `setOnline`/`setOffline`, `getOnlineUsers`, `getUser`, `getLeaderboard`, `updateStats`; inner `User` (username, password, name, email, wins, losses, draws) | SecurityUtils, File I/O |
| **RateLimiter** | Service | Per-identifier (`clientAddress:username`) tracking: `AttemptInfo` (attempts, firstAttempt, lockoutUntil); `isRateLimited`, `recordFailedAttempt`, `recordSuccess`, `getRemainingLockoutTime`; 5 attempts → 15 min lockout; 1 min sliding window | — |
| **SecurityUtils** | Static utility | `hashPassword`, `verifyPassword`, `isPlaintext`; `validateUsername`, `validatePassword`, `validateEmail`, `validateName`; `sanitize` | — |
| **Protocol** | Static utility | `parseMessage(String)` → `Map<String,Object>`; `createMessage(Map)`, `createErrorMessage`, `createSuccessMessage` | — |

**Control flow (server):** The main thread runs `GameServer.start()`: `ServerSocket.accept()` in a loop; for each `Socket`, creates `ClientHandler(socket, userManager, this)` and `handler.start()`. Each `ClientHandler.run()` loops on `in.readLine()`; for each message it calls `handleMessage` → type-specific handler. Handlers that affect game state (e.g. `handleMove`) call `server.processMove(...)`, which runs in the same `ClientHandler` thread, acquires the `GameSession` from `games`, and calls `game.makeMove(...)`. No dedicated thread exists per game; concurrency across matches comes from multiple `ClientHandler` threads and thread-safe shared structures (`ConcurrentHashMap`).

---

#### 3.4.2 Client-Side Component Decomposition

| Component | Type | Responsibility | Used in |
|-----------|------|----------------|---------|
| **GameClient** | Network + state | `connect()` (Socket, `ServerListener`); `handleServerMessage` (dispatch by `type` to handlers); send methods (`register`, `login`, `listPlayers`, `challenge`, `respondToChallenge`, `makeMove`, `requestRematch`, `respondToRematch`, `requestLeaderboard`, `logout`); state: `currentGameId`, `currentOpponent`, `currentBoard`, `currentPlayer`, `inGame`; `handleServerDisconnection` | CLI |
| **GameUI** | CLI interface | `run()`: read line, parse command (register, login, list, challenge, accept, reject, move, board, stats, rematch, leaderboard, logout, help); `pendingChallenger`, `pendingRematchRequester`; `showMessage`, `showError`, `showStats`, `updatePlayersList`, `showChallenge`, `showBoard` | CLI |
| **ServerListener** | Thread | `run()`: loop `in.readLine()` → `client.handleServerMessage(msg)`; on null/IOException → `client.handleServerDisconnection(reason)` | CLI |
| **GameClientGUI** | GUI + network + state | Same logical responsibilities as `GameClient`; in addition: `loginFrame`, `registrationDialog`, `mainFrame` (lobby), `gameFrame` (board); `handleServerMessage` wrapped in `SwingUtilities.invokeLater`; `connect()`, `createMainWindow`, `createGameWindow`, `updateBoardDisplay`, `showStatus`, `handleServerDisconnection` (dialog, dispose, `System.exit(0)`) | GUI |
| **ServerListenerGUI** | Thread | Same as `ServerListener` but calls `GameClientGUI.handleServerMessage` and `handleServerDisconnection` | GUI |
| **Player** | Model | `username`, `name`, `email`, wins, losses, draws, `authenticated`; getters/setters and `incrementWins/Losses/Draws` for local display | CLI, GUI |

**Control flow (client):**  
- **CLI:** Main thread runs `GameClient.connect()` then `GameUI.run()` (blocking on `Scanner`). `ServerListener` runs in a separate thread; incoming messages are handled in that thread via `GameClient.handleServerMessage` (which calls `GameUI.showMessage` / `showError` / etc.).  
- **GUI:** EDT runs Swing; `GameClientGUI` is created and shows `loginFrame`. `connect()` spawns `ServerListenerGUI`. Incoming messages are processed in the listener thread but all UI updates are dispatched with `SwingUtilities.invokeLater` to the EDT.

---

#### 3.4.3 Core Data Structures

**GameServer (in-memory):**

| Structure | Type | Key | Value | Purpose |
|-----------|------|-----|-------|---------|
| `clients` | `ConcurrentHashMap<String, ClientHandler>` | username | ClientHandler | Send messages to a logged-in user |
| `games` | `ConcurrentHashMap<String, GameSession>` | gameId (UUID) | GameSession | Locate game for MOVE, LEAVE_GAME, disconnect |
| `pendingChallenges` | `ConcurrentHashMap<String, String>` | challenger | opponent | Validate CHALLENGE_RESPONSE |
| `pendingRematches` | `ConcurrentHashMap<String, String>` | requester | opponent | Validate REMATCH_RESPONSE |
| `lastOpponents` | `ConcurrentHashMap<String, String>` | player | last opponent | REMATCH_REQUEST when `opponent` omitted |

**UserManager:**

| Structure | Type | Purpose |
|-----------|------|---------|
| `users` | `ConcurrentHashMap<String, User>` | username → User (password, name, email, wins, losses, draws) |
| `onlineUsers` | `ConcurrentHashMap<String, String>` | username → sessionId |
| `users.txt` | File | Persistent; one line per user: `username,password,name,email,wins,losses,draws` (or 5-field legacy) |

**RateLimiter:**

| Structure | Type | Purpose |
|-----------|------|---------|
| `attempts` | `ConcurrentHashMap<String, AttemptInfo>` | identifier (e.g. `clientAddress:username`) → `AttemptInfo` (attempts, firstAttempt, lockoutUntil) |

**GameSession:**

| Field | Type | Purpose |
|-------|------|---------|
| `board` | `char[3][3]` | Cell values: `' '`, `'X'`, `'O'` |
| `currentPlayer` | String | Username of player to move |
| `moveCount` | int | Draw when 9 |

**Protocol (wire format):** One message per line; JSON-like `{"type":"MOVE","gameId":"...", "data":{"x":"1","y":"2"}}`. Nested `data` used for MOVE coordinates.

---

#### 3.4.4 Threading Model

**Server:**

| Thread | Role | Blocking / work |
|--------|------|------------------|
| Main | `GameServer.start()`: `ServerSocket.accept()` in `while(true)` | Blocks on `accept()`; then creates and starts `ClientHandler` |
| ClientHandler (N threads) | One per connected client | Blocks on `in.readLine()`; on message: parse, dispatch, possibly call `GameServer.processMove`/`broadcastPlayerList`/`sendToPlayer` (all in same thread) |

`GameSession` is not a thread; it is only invoked from `ClientHandler` threads via `GameServer.processMove`. Concurrent matches are safe because: (i) each game is a distinct `GameSession` in `games`; (ii) a given `GameSession` is only accessed by the two `ClientHandler` threads of the two players, and a single MOVE is processed by one `ClientHandler`; (iii) shared maps are `ConcurrentHashMap`.

**Client (CLI):** Main thread = `GameUI.run()` (console I/O). One `ServerListener` thread = `in.readLine()` and `handleServerMessage`.

**Client (GUI):** EDT = all Swing. One `ServerListenerGUI` thread = `in.readLine()` and `handleServerMessage`; the latter uses `SwingUtilities.invokeLater` before any UI update.

---

#### 3.4.5 Component Interfaces and Key Invocations

**Server (simplified):**

- `GameServer`: `addClient(ClientHandler)`, `removeClient(ClientHandler)`, `broadcastPlayerList()`, `sendChallenge(opponent, challenger)`, `handleChallengeResponse(challenger, opponent, response)`, `processMove(gameId, player, x, y)`, `handleLeaveGame(gameId, player)`, `sendRematchRequest(opponent, requester)`, `handleRematchResponse(requester, opponent, response)`, `sendLeaderboard(ClientHandler)`, `getLastOpponent(username)`.
- `ClientHandler` → `GameServer`: the above + `sendToPlayer` (package/private via `GameServer`).
- `ClientHandler` → `UserManager`: `register`, `login`, `isOnline`, `setOnline`, `setOffline`, `getOnlineUsers`, `getUser`, `updateStats`.
- `ClientHandler` → `Protocol`: `parseMessage`, `createMessage`, `createErrorMessage`, `createSuccessMessage`.
- `GameServer` → `GameSession`: constructor, `makeMove`, `getPlayer1/2`, `getCurrentPlayer`, `isGameOver`, `getResultFor`, `getBoardMap`.
- `GameServer` → `UserManager`: `updateStats`, `getOnlineUsers`.
- `UserManager` → `SecurityUtils`: `hashPassword`, `verifyPassword`, `isPlaintext`.
- `ClientHandler` → `SecurityUtils`: `validateUsername`, `validatePassword`, `validateName`, `validateEmail`, `sanitize`.
- `ClientHandler` → `RateLimiter`: `isRateLimited`, `recordFailedAttempt`, `recordSuccess`, `getRemainingLockoutTime`.

**Client:** `GameClient` / `GameClientGUI` build `Map` and call `Protocol.createMessage` then `sendMessage` (i.e. `out.println`). `handleServerMessage` uses `Protocol.parseMessage` and switches on `type`.

---

#### 3.4.6 Key Low-Level Sequences

**Login (success):**  
Client: `login(u,p)` → `out.println(Protocol.createMessage({type,username,password}))`.  
Server: `ClientHandler.run` → `readLine` → `handleMessage` → `handleLogin`: `SecurityUtils.sanitize`; `rateLimiter.isRateLimited`; `userManager.login`; on success: `userManager.setOnline`, `server.addClient`, `handler.sendMessage(LOGIN_SUCCESS)`; `server.broadcastPlayerList()`.

**Challenge and start game:**  
Client A: `challenge(B)` → CHALLENGE.  
Server: `handleChallenge` → `server.sendChallenge(B, A)`: `pendingChallenges.put(A,B)`, send CHALLENGE to B’s `ClientHandler`.  
Client B: `handleChallenge` → accept → CHALLENGE_RESPONSE.  
Server: `handleChallengeResponse` → `server.handleChallengeResponse(A, B, ACCEPT)`: `pendingChallenges.remove(A)`, send CHALLENGE_RESPONSE to A; `startGame(A,B)`: new `GameSession(gameId, A, B)`, `games.put(gameId, session)`, send START_GAME to A and B.

**Move:**  
Client: `makeMove(x,y)` → MOVE with `gameId`, `data{x,y}`.  
Server: `handleMove` → `server.processMove(gameId, username, x, y)`: get `GameSession` from `games`; `game.makeMove(username, x, y)` (in this `ClientHandler` thread); build UPDATE, `sendToPlayer` both; if `game.isGameOver()`: `userManager.updateStats` for both, build RESULT for each, `sendToPlayer`, `lastOpponents.put`, `games.remove(gameId)`.

**Disconnect during game:**  
`ClientHandler.run` exits (readLine null or IOException) → `cleanup` → `userManager.setOffline`, `server.removeClient`. `removeClient` → `findGameByPlayer`; if found and not over: `userManager.updateStats` (disconnected=LOSS, opponent=WIN), `lastOpponents.put`, send OPPONENT_DISCONNECTED to opponent, `games.remove`.

---

#### 3.4.7 State Models

**ClientHandler (connection/session):**

```
[Connected, unauthenticated] ──LOGIN success──▶ [Authenticated] ──LOGOUT / disconnect──▶ [Cleaned up, socket closed]
                     │
                     └── REGISTER, LIST_PLAYERS (rejected), CHALLENGE (rejected), etc. before LOGIN
```

After authentication, the same handler serves LIST_PLAYERS, CHALLENGE, MOVE, REMATCH_*, LEADERBOARD, LEAVE_GAME.

**GameSession (match lifecycle):**

```
[Created: board empty, currentPlayer=player1, gameOver=false]
       │
       ▼
[Running: makeMove updates board, currentPlayer, moveCount; checkWin or moveCount==9 → gameOver]
       │
       ▼
[Ended: gameOver=true, winner=player|"DRAW"] → getResultFor(WIN|LOSS|DRAW); GameServer removes from `games`
```

**Client (coarse UI states):**  
(1) Pre-connect / Login screen.  
(2) Lobby (authenticated, main window / list).  
(3) In-game (game window, `inGame=true`, `currentGameId` set).  
(4) Post-game (result shown, Rematch / Back to Lobby).  
Transitions are driven by START_GAME, UPDATE, RESULT, OPPONENT_DISCONNECTED, and user actions (Rematch, Back to Lobby, window close → LEAVE_GAME).

---

#### 3.4.8 Low-Level Diagram (Server Modules and Data)

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                    GameServer (main thread: accept)      │
                    │  clients ──────────┬─────────────────────────────────   │
                    │  games ────────────┼──▶ GameSession (per match)         │
                    │  pendingChallenges │    - board[3][3], currentPlayer    │
                    │  pendingRematches  │    - makeMove, getBoardMap         │
                    │  lastOpponents     └──────────────────────────────────  │
                    └───────────────────────────┬─────────────────────────────┘
                                                │
         ┌──────────────────────────────────────┼──────────────────────────────────────┐
         │                    ClientHandler (thread per client)                         │
         │  in.readLine() ──▶ Protocol.parse ──▶ handleMessage ──▶ handleLogin,         │
         │       │                │                    │           handleMove, ...      │
         │       │                │                    └──────────▶ server.processMove  │
         │       │                │                                server.sendChallenge │
         │       ▼                │                                server.broadcast...  │
         │  out.println ◀── Protocol.create ◀── (response / push)                      │
         └───────────────────────────┬─────────────────────────────────────────────────┘
                                     │
         ┌───────────────────────────┼───────────────────────────┐
         ▼                           ▼                           ▼
  ┌─────────────┐           ┌─────────────┐           ┌─────────────────┐
  │ UserManager │           │ RateLimiter │           │ SecurityUtils   │
  │ users       │           │ attempts    │           │ Protocol        │
  │ onlineUsers │           │ AttemptInfo │           │ (parse/create)  │
  │ users.txt   │           └─────────────┘           └─────────────────┘
  └─────────────┘
```

---

## 4. Communication Protocol

### 4.1 Transport and Format

- **Transport:** TCP; one connection per client, kept open for the session.
- **Framing:** One message per line (newline-delimited). No explicit length prefix.
- **Encoding:** UTF-8 text.
- **Format:** JSON-like objects: `{"key":"value",...}`. Strings are quoted; nested objects used for `data` (e.g. in MOVE). The `Protocol` class implements a custom parser and builder.

### 4.2 Message Types (Client → Server)

| Type | Purpose | Fields |
|------|---------|--------|
| REGISTER | Create account | `username`, `password`, `name`, `email` |
| LOGIN | Authenticate | `username`, `password` |
| LIST_PLAYERS | Get online players | — |
| CHALLENGE | Request game | `opponent` |
| CHALLENGE_RESPONSE | Accept/reject challenge | `challenger`, `response` ("ACCEPT" / "REJECT") |
| MOVE | Play a cell | `gameId`, `data`→`{"x":"0","y":"1"}` |
| REMATCH_REQUEST | Ask for rematch | `opponent` (optional; server can use last opponent) |
| REMATCH_RESPONSE | Accept/reject rematch | `opponent`, `response` |
| LEADERBOARD | Request leaderboard | — |
| LEAVE_GAME | Leave active game (e.g. close window) | `gameId` |
| LOGOUT | Disconnect | — |

### 4.3 Message Types (Server → Client)

| Type | Purpose | Fields |
|------|---------|--------|
| LOGIN_SUCCESS | Login OK | `username`, `wins`, `losses`, `draws`, `name`, `email` |
| SUCCESS | Generic success | `message` |
| ERROR | Error | `message` |
| PLAYERS_LIST | Online players | `players` (comma-separated) |
| CHALLENGE | Incoming challenge | `challenger` |
| CHALLENGE_RESPONSE | Challenge accepted/rejected | `opponent`, `response` |
| START_GAME | Game started | `gameId`, `player1`, `player2`, `currentPlayer` |
| UPDATE | Board update | `gameId`, `board` (map "x,y"→"X"/"O"/" "), `currentPlayer` |
| RESULT | Game over | `gameId`, `result` ("WIN"/"LOSS"/"DRAW"), `board` |
| OPPONENT_DISCONNECTED | Opponent left | `gameId` |
| REMATCH_REQUEST | Incoming rematch | `requester` |
| REMATCH_RESPONSE | Rematch accepted/rejected | `opponent`, `response` |
| LEADERBOARD | Top players | `data` (e.g. `"1,alice,5,2,1|2,bob,3,4,0"` → rank,user,wins,losses,draws) |

### 4.4 Protocol Assumptions

- Each message is a single line; newlines inside string values are not escaped (not expected in current fields).
- The server infers the sender from the authenticated session (e.g. `ClientHandler`’s `username`); the optional `player` in MOVE is not used for authorization.
- `board` in UPDATE/RESULT uses keys `"0,0"` … `"2,2"` and values `" "`, `"X"`, `"O"`.
- For REMATCH_REQUEST without `opponent`, the server uses `lastOpponents`.

---

## 5. Architecture and Class Descriptions

### 5.1 Server-Side

#### **Server**
- **Role:** Entry point; creates `GameServer` and starts it.
- **Responsibilities:** `main(String[])` only.

#### **GameServer**
- **Role:** Core server; manages connections, clients, games, challenges, and rematches.
- **Responsibilities:**
  - `ServerSocket` accept loop; spawns `ClientHandler` per `Socket`.
  - Maps: `clients` (username→ClientHandler), `games` (gameId→GameSession), `pendingChallenges`, `pendingRematches`, `lastOpponents`.
  - `addClient` / `removeClient` (including disconnect-in-game: forfeit loss, OPPONENT_DISCONNECTED, remove game).
  - `broadcastPlayerList` to all authenticated clients.
  - `sendChallenge`, `handleChallengeResponse`, `startGame`.
  - `processMove`: validate turn and cell, apply move in `GameSession`, broadcast UPDATE; on game over update `UserManager`, send RESULT, update `lastOpponents`, remove game.
  - `handleLeaveGame`: same forfeit/notification logic as disconnect.
  - `sendRematchRequest`, `handleRematchResponse`, `sendLeaderboard`, `getLastOpponent`, `sendToPlayer`, `findGameByPlayer`.

#### **ClientHandler** (extends `Thread`)
- **Role:** One thread per client; reads lines, parses with `Protocol`, dispatches by `type`, enforces authentication for game-related messages.
- **Responsibilities:**
  - `BufferedReader` / `PrintWriter` on the client `Socket`.
  - `handleMessage` → `handleRegister`, `handleLogin`, `handleListPlayers`, `handleChallenge`, `handleChallengeResponse`, `handleMove`, `handleLogout`, `handleRematchRequest`, `handleRematchResponse`, `handleLeaderboard`, `handleLeaveGame`.
  - Login: `SecurityUtils.sanitize`; `RateLimiter` (lockout after 5 failed attempts); `UserManager.login`; on success set `username`, `authenticated`, `userManager.setOnline`, `server.addClient`, send LOGIN_SUCCESS, `broadcastPlayerList`.
  - Register: `SecurityUtils.validate*` for username, password, name, email; `UserManager.register`.
  - `cleanup` on disconnect or LOGOUT: `setOffline`, `removeClient`, `broadcastPlayerList`, close `Socket`.
  - `sendMessage` to this client.

#### **GameSession**
- **Role:** Encapsulates one Tic-Tac-Toe match.
- **Responsibilities:**
  - 3×3 `char[][]` board; `player1`, `player2`, `currentPlayer`, `winner`, `gameOver`, `moveCount`.
  - `makeMove(player,x,y)`: check turn, bounds, empty cell; set X/O; `checkWin` (row, column, diagonals); draw if 9 moves; else switch `currentPlayer`.
  - `getBoardString`, `getBoardMap` (for Protocol), `getResultFor(player)` (WIN/LOSS/DRAW/ONGOING).

#### **UserManager**
- **Role:** User persistence and online presence.
- **Responsibilities:**
  - Load/save `users.txt`; support 5-, 7-, and 8-field formats for backward compatibility.
  - `register(username, password, name, email)`: hash password via `SecurityUtils.hashPassword`, store `User`, `saveUsers`.
  - `login`: support hashed and plaintext (migrate to hash on successful check); `isOnline`, `setOnline(sessionId)`, `setOffline`.
  - `getOnlineUsers`, `getUser`, `getLeaderboard(limit)`, `updateStats(username, result)`.
  - Inner class **User**: username, password, name, email, wins, losses, draws; `toFileString` for persistence.

#### **RateLimiter**
- **Role:** Login brute-force protection.
- **Responsibilities:**
  - Per-identifier (e.g. `clientAddress:username`) tracking: `AttemptInfo` (attempts, firstAttempt, lockoutUntil).
  - `isRateLimited`: true if within lockout; reset if lockout or 1-minute window expired.
  - `recordFailedAttempt`: increment; if ≥5, set lockout 15 minutes.
  - `recordSuccess`: remove entry. `getRemainingLockoutTime` (seconds).

#### **SecurityUtils**
- **Role:** Password hashing, validation, and sanitization.
- **Responsibilities:**
  - `hashPassword`: 16-byte salt, SHA-256 with 10000 iterations, salt+hash Base64.
  - `verifyPassword`, `isPlaintext` (heuristic for migration).
  - `validateUsername` (3–20, alphanumeric/underscore), `validatePassword` (min 6), `validateEmail` (regex), `validateName` (trim, remove `<>\"'&`).
  - `sanitize`: remove control chars and `<>\"'&`.

#### **Protocol**
- **Role:** Message parsing and construction.
- **Responsibilities:**
  - `parseMessage(String)`: manual JSON-like parse → `Map<String,Object>`; supports nested `data` object.
  - `createMessage(Map)`, `createSimpleMessage`, `createErrorMessage`, `createSuccessMessage`.

---

### 5.2 Client-Side (Shared Model)

#### **Player**
- **Role:** Local model of the current user.
- **Responsibilities:** username, name, email, password (or placeholder), wins, losses, draws, authenticated; getters/setters and `incrementWins/Losses/Draws` (used by GUI for immediate display; server remains authority).

---

### 5.3 CLI Client

#### **Client**
- **Role:** CLI entry point; delegates to `GameClient.main`.

#### **GameClient**
- **Role:** Network and game-state logic for the CLI client.
- **Responsibilities:**
  - `connect()`: `Socket` to `SERVER_HOST:12345`, `ServerListener` for incoming messages.
  - `handleServerMessage`: switch on `type` → `handleLoginSuccess`, `handlePlayersList`, `handleChallenge`, `handleChallengeResponse`, `handleStartGame`, `handleUpdate`, `handleResult`, `handleOpponentDisconnected`, `handleRematchRequest`, `handleRematchResponse`, `handleLeaderboard`; SUCCESS/ERROR to `GameUI`.
  - Send methods: `register`, `login`, `listPlayers`, `challenge`, `respondToChallenge`, `makeMove`, `requestRematch`, `respondToRematch`, `requestLeaderboard`, `logout`; each builds a map and `Protocol.createMessage` + `sendMessage`.
  - State: `currentGameId`, `currentOpponent`, `currentBoard`, `currentPlayer`, `inGame`.
  - `handleServerDisconnection`: print, `disconnect`, `System.exit(0)`.
  - `disconnect`, getters for `Player`, board, `inGame`, `currentPlayer`.

#### **GameUI**
- **Role:** CLI interface and command loop.
- **Responsibilities:**
  - `run()`: read commands (register, login, list, challenge, accept, reject, move, board, stats, rematch, leaderboard, logout, quit, help).
  - `pendingChallenger`, `pendingRematchRequester` for accept/reject.
  - `showMessage`, `showError`, `showStats`, `updatePlayersList`, `showChallenge`, `setPendingRematchRequester`, `showBoard`, `handleRegister` (prompts for name/email).

#### **ServerListener** (extends `Thread`)
- **Role:** Read lines from `GameClient`’s `BufferedReader`; dispatch to `client.handleServerMessage`; on EOF/IOException call `client.handleServerDisconnection`.

---

### 5.4 GUI Client

#### **ClientGUI**
- **Role:** GUI entry point; delegates to `GameClientGUI.main`.

#### **GameClientGUI**
- **Role:** Full GUI client: login, lobby, game window, protocol handling.
- **Responsibilities:**
  - **Login:** `loginFrame` (username, password, show/hide, Register/Login). Register opens modal `registrationDialog` (username, password, name, email); on submit connects if needed, sends REGISTER; on SUCCESS "Registration successful" closes dialog, shows message, disconnects; on ERROR shows in dialog. `isRegistering` used to route SUCCESS/ERROR.
  - **Connect:** `connect()`: `Socket`, `ServerListenerGUI`; reuse if already connected.
  - **Lobby:** `mainFrame` – profile (username, name, email, stats), Refresh Players, Leaderboard, Logout; `JList` of players, "Challenge Selected Player".
  - **Game:** `gameFrame` – 3×3 buttons, `currentPlayerLabel`, Rematch, Back to Lobby, status. On close: if in game and `currentGameId` set, send LEAVE_GAME then dispose and show lobby.
  - **Message handling:** `handleServerMessage` wrapped in `SwingUtilities.invokeLater`; same logical handlers as `GameClient` (LOGIN_SUCCESS→`createMainWindow`, listPlayers; START_GAME→`createGameWindow`; UPDATE→`updateBoardDisplay`; RESULT→disable board, update `Player` stats, enable Rematch/Lobby; OPPONENT_DISCONNECTED→increment wins, dialog, dispose game, show lobby; REMATCH_REQUEST/RESPONSE, CHALLENGE/CHALLENGE_RESPONSE, LEADERBOARD in dialogs or status).
  - `updateBoardDisplay`, `updateStatsDisplay`, `showStatus` (status bar and/or `gameStatusLabel`), `goBackToLobby`, `logout`, `disconnect`, `handleServerDisconnection` (dialog then dispose all and `System.exit(0)`).

#### **ServerListenerGUI** (extends `Thread`)
- **Role:** Same as `ServerListener` but for `GameClientGUI` and `handleServerDisconnection`.

---

## 6. Assumptions

1. **Network:** Stable TCP; clients and server on same machine or trusted LAN; no NAT/firewall issues for port 12345.
2. **Deployment:** Server started before clients; `users.txt` in the server working directory; write permission for `users.txt` and `users.txt.tmp`.
3. **Concurrency:** One `ClientHandler` per connection; `ConcurrentHashMap` and atomic/thread-safe structures where shared.
4. **Identifiers:** Usernames are unique; `gameId` is UUID; `sessionId` used for online tracking.
5. **Security:** Passwords and hashes only in memory and `users.txt`; no TLS; rate limiting and hashing reduce impact of some attacks only.
6. **Compatibility:** `UserManager` tolerates older 5- and 8-field `users.txt` formats; `SecurityUtils.isPlaintext` used to migrate to hashed storage.
7. **GUI:** Swing on a single EDT; all UI updates from `handleServerMessage` go through `SwingUtilities.invokeLater`.
8. **One game per player:** A player is in at most one active game; `findGameByPlayer` returns at most one `gameId`.

---

## 7. Implementation Notes

- **Port and host:** `GameServer` and clients use `PORT=12345` and `SERVER_HOST="localhost"`; changing deployment requires edits or properties/env.
- **Users file:** `UserManager` uses atomic write (temp file + rename) and optional permission tightening (`setSecureFilePermissions`); on Windows the chmod path is not used.
- **Validation:** Registration uses `SecurityUtils.validate*`; login uses `sanitize`; `ClientHandler` does not re-validate `gameId` format (relies on server-side `games` lookup).
- **Rematch:** `REMATCH_RESPONSE` uses `opponent` to denote the peer (requester or responder depending on direction); server’s `handleRematchResponse` matches `pendingRematches` and starts a new game on ACCEPT.

---

## 8. Testing

### 8.1 Approach

- **Manual testing** driven by `HOW_TO_RUN.txt`: start server, multiple CLI and/or GUI clients, registration, login, list, challenge, accept, moves, win/draw, rematch, leaderboard, leave, logout, disconnect, and window close during game.
- **No automated unit or integration tests** are present (JUnit jars exist in `lib/` but are unused).

### 8.2 Tested Flows

- Registration (CLI with name/email, GUI with dialog) and login.
- Challenge and accept/reject; START_GAME, UPDATE, RESULT.
- Draw and win by row/column/diagonal.
- Rematch request/accept and new game.
- Opponent disconnect and LEAVE_GAME: remaining player gets WIN, OPPONENT_DISCONNECTED, return to lobby.
- Leaderboard and stats refresh.
- Rate limiting: multiple failed logins lead to temporary lockout.
- GUI: registration success closes dialog and disconnects; LEAVE_GAME on game window close.

---

## 9. Evaluation

### 9.1 Not Implemented or Weakened

| Item | Notes |
|------|--------|
| **Automated tests** | No JUnit (or other) tests; regression and refactoring rely on manual runs. |
| **Configurable host/port** | Hard-coded; no config file or env variables. |
| **TLS/SSL** | All traffic is plaintext; unsuitable for untrusted networks. |
| **Password in LOGIN_SUCCESS** | Correctly omitted; `GameClientGUI` sets a placeholder (`"***"`) for any local use. |
| **Reconnection** | No automatic reconnect; client exits or user must restart. |
| **Multiple games per user** | Design assumes one active game per user; concurrent games would require extra logic. |
| **Chat or in-game messaging** | Not implemented. |
| **Spectating or replays** | Not implemented. |

### 9.2 Behavioural Issues and Edge Cases

| Issue | Description |
|-------|-------------|
| **`SecurityUtils.validateName` / `validateEmail`** | Registration requires non-empty name and email; `validateName` throws if empty. `UserManager` and `User.toFileString` support empty name/email for older 5-field format, but registration paths enforce them. |
| **`isPlaintext` heuristic** | `SecurityUtils.isPlaintext` uses length and regex; in rare cases could misclassify; migration is best-effort. |
| **Leaderboard in CLI** | `GameClient.handleLeaderboard` prints to stdout instead of using `GameUI`; behaviour differs from GUI. |
| **`users.txt` format** | Multiple formats (5/7/8 fields) and optional nickname increase complexity and risk of parse errors on hand-edits. |
| **Rematch after decline** | CurrentOpponent is kept for rematch; if the other player goes offline, "Request Rematch" can still be sent and will fail with "User not available". |
| **Double stats update on OPPONENT_DISCONNECTED (GUI)** | Server already updates stats; `GameClientGUI.handleOpponentDisconnected` also calls `player.incrementWins()`. For the session this is redundant but consistent; on next login, server data overwrites. |

### 9.3 Design and Development Challenges

| Challenge | Approach / Outcome |
|-----------|--------------------|
| **Parsing JSON without libraries** | Custom `Protocol` parser for the required subset; careful handling of nested `data` and commas. More complex messages would benefit from a real JSON library. |
| **Swing and listener thread** | `ServerListenerGUI` runs in a background thread; all UI updates and `handleServerDisconnection` are dispatched via `SwingUtilities.invokeLater` to avoid EDT violations. |
| **Registration and connection** | GUI registers before login; `connect()` is called on first Register or Login. After successful registration, client disconnects; user must Login again (new connection). |
| **Window close during game** | Game window uses `DO_NOTHING_ON_CLOSE` and a `WindowAdapter` to send LEAVE_GAME, then dispose and show lobby so state stays consistent. |
| **Rate limiter key** | Using `clientAddress + ":" + username` limits effectiveness when many IPs are used; could be strengthened with global per-username limits. |
| **File locking** | No file locking on `users.txt`; under high concurrency, last write could overwrite concurrent updates. Acceptable for a small, low-load deployment. |

---

## 10. Results and Discussion

### 10.1 Achieved Goals

- **Architecture:** A clear client–server split with a JSON-like protocol, centralised game and user state, and two client UIs (CLI and GUI).
- **Gameplay:** Full Tic-Tac-Toe with turns, win/draw, and correct attribution of WIN/LOSS/DRAW and forfeits on disconnect/leave.
- **User management:** Registration (with name/email), hashed passwords, persistence in `users.txt`, and online list.
- **Security-oriented measures:** Hashing, validation, sanitization, and login rate limiting.
- **Resilience:** Disconnect and LEAVE_GAME update stats and notify the other player; GUI and CLI both return to a consistent lobby or exit.

### 10.2 Observations

- **Protocol:** The text, JSON-like format is sufficient for the current message set and makes debugging easier. A formal specification (e.g. in a separate doc or IDL) would help for future extensions.
- **Scalability:** One thread per client and in-memory maps are fine for tens of concurrent users; for higher load, a pool of workers, async I/O, or a different architecture would be needed.
- **Security:** In a real deployment, TLS, stronger secrets management, and stricter rate limiting would be necessary; the current measures are appropriate for a learning or LAN project.
- **Testing:** The absence of automated tests increases the risk of regressions when changing `Protocol`, `GameSession`, or `UserManager`; adding a small set of unit tests for these would be valuable.

### 10.3 Possible Extensions

- Configuration (host, port, paths) via file or environment.
- Optional TLS.
- Automated tests for `Protocol`, `GameSession`, `UserManager`, and `SecurityUtils`.
- Optional name/email in registration.
- Unified leaderboard handling in CLI (e.g. through `GameUI`).
- Stricter `users.txt` format and migration script for legacy data.

---

## 11. UML Class Diagram (Simplified)

```
┌─────────────┐       ┌──────────────┐       ┌────────────────┐
│   Server    │       │  GameServer  │       │ ClientHandler  │
├─────────────┤       ├──────────────┤       ├────────────────┤
│ + main()    │──────▶│ - clients    │◀──────│ - socket       │
└─────────────┘       │ - games      │       │ - userManager  │
                      │ - ...        │       │ - server       │
                      │ + start()    │       │ - username     │
                      │ + addClient()│       │ + run()        │
                      │ + processMove│       │ - handleMsg()  │
                      └──────┬───────┘       └───────┬────────┘
                             │                       │
              ┌──────────────┼──────────────┐        │
              ▼              ▼              ▼        │
     ┌──────────────┐ ┌────────────┐ ┌───────────┐  │
     │ UserManager  │ │ GameSession│ │ RateLimit │  │
     ├──────────────┤ ├────────────┤ ├───────────┤  │
     │ + register() │ │ + makeMove │ │ + isRate  │  │
     │ + login()    │ │ + getBoard │ │   Limited │  │
     │ + load/save  │ │ + checkWin │ │ + record  │  │
     └──────────────┘ └────────────┘ └───────────┘  │
                                                     │
┌─────────────┐       ┌──────────────┐       ┌──────┴────────┐
│ GameClient  │       │ GameClientGUI│       │ ServerListener│
│   / GUI     │       │              │       │   / GUI       │
├─────────────┤       ├──────────────┤       ├───────────────┤
│ - socket    │◀──────│ - loginFrame │       │ - in          │
│ - player    │       │ - mainFrame  │       │ - client      │
│ - listener  │       │ - gameFrame  │       │ + run()       │
│ + connect() │       │ + handleSrv  │       │ + stopListen  │
│ + handleSrv │       │   Msg()      │       └───────────────┘
│   Msg()     │       │ + makeMove() │
└──────┬──────┘       └──────┬───────┘
       │                     │
       │            ┌────────┴────────┐
       │            │     Player      │
       │            ├─────────────────┤
       └───────────▶│ - username      │
                    │ - wins/losses/  │
                    │   draws         │
                    └─────────────────┘

       ┌────────────┐     ┌───────────────┐
       │  Protocol  │     │ SecurityUtils │
       ├────────────┤     ├───────────────┤
       │ + parse()  │     │ + hashPwd()   │
       │ + create() │     │ + verifyPwd() │
       │ + create   │     │ + validate*() │
       │   Error()  │     │ + sanitize()  │
       └────────────┘     └───────────────┘
```

---

## 12. Conclusion

The project delivers a working, multi-player Tic-Tac-Toe game with a TCP-based, JSON-like protocol, file-based user storage, and both CLI and GUI clients. The report has described the analysis, design, architecture, communication protocol, main design decisions, and the role of each class. Documented assumptions, implementation notes, and an evaluation highlight missing pieces (e.g. automated tests, TLS, configurable host/port), behavioural nuances, and design/development difficulties. The results and discussion summarise what was achieved and suggest concrete improvements for maintainability, security, and scalability.
