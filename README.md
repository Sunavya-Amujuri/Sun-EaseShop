# E-Commerce Application (with AI features)

The same store as the plain version, with ten AI features built into it and a monitoring stack that
shows what those features are doing.

**Two folders make one app:**

| Folder | What it is |
|---|---|
| `E-Commerce-Backend` | Spring Boot API with Spring AI, runs on port 8080 |
| `ecom-ui` | React storefront, runs on port 5173 |

Both have to be running at the same time.

---

## What the AI adds

Everything the plain app does, plus:

| Feature | Where you see it |
|---|---|
| Search that understands meaning | The header search bar |
| Ask a question about a product | Product page |
| Recommendations from order history | Home page |
| Suggestions for what is in the cart | Cart page |
| Return eligibility with an explanation | Order detail page |
| A shopping assistant that knows your orders | Floating button, bottom corner |
| Support tickets sorted automatically | Admin panel, Support tab |
| An assistant that can act on tickets | Admin panel, Support tab |
| An analytics assistant that reads real numbers | Admin panel, Analytics tab |
| Generated product descriptions and photos | Admin panel, Add product |

---

## Before you start

| Tool | Version | Check it with |
|---|---|---|
| Java | 17 | `java -version` |
| Maven | 3.9 or newer | `mvn -v` |
| Node.js | 18 or newer | `node -v` |
| Docker Desktop | Any current version | `docker -v` |

**Docker has to be running before you start the backend.** This app does not use a Postgres you
installed yourself. It uses a Postgres image with the pgvector extension, and Spring Boot starts that
container for you.

You do not need PostgreSQL installed on your machine for this version.

---

## Step 1: The database starts itself

There is nothing to create by hand. In the project root there is a `docker-compose.yml`:

```yaml
services:
  pgvector:
    image: 'pgvector/pgvector:pg16'
    environment:
      - 'POSTGRES_DB=telusko'
      - 'POSTGRES_PASSWORD=telusko'
      - 'POSTGRES_USER=postgres'
    labels:
      - "org.springframework.boot.service-connection=postgres"
    ports:
      - '5432'
```

When the app starts, `spring-boot-docker-compose` reads this file, starts the container, and takes the
host, port, username and password straight from it. That is why there is no database password to
configure anywhere.

**Why pgvector and not plain Postgres.** It is ordinary Postgres with one extension compiled in, and
that extension is what lets the database compare meanings rather than only match text. Every normal
table, query and JPA mapping works exactly as before.

Make sure Docker Desktop is open. If it is not, the app fails at startup with a Docker error.

---

## Step 2: Get the API keys

This version needs one more key than the plain app, and that one is not optional.

| Service | What it does | Needed? |
|---|---|---|
| **OpenAI** | Every AI feature | Yes, nothing AI works without it |
| Mailtrap | Delivers the signup verification code | Yes, you cannot create an account without it |
| Cloudinary | Stores product images | Yes, if you want to add products |
| Razorpay | Online payment at checkout | Only for the payment step |
| GitHub OAuth | The "Sign in with GitHub" button | Optional |

### 2.1 OpenAI, the important one

This app uses three OpenAI models: `gpt-4o` for chat, `text-embedding-3-small` for search, and
`gpt-image-1` for product photos.

1. Go to **platform.openai.com** and sign in.
2. **Add credit first.** Open **Settings**, then **Billing**, and add a payment method with a small
   amount. A new key with no credit fails on the first call with a quota error, and that is the single
   most common reason this app does not work.
3. Open **API keys** from the left menu, or go to **platform.openai.com/api-keys**.
4. Press **Create new secret key**, give it a name, and create it.
5. **Copy it immediately.** It is shown once. If you close the dialog you have to make a new one.

**Do not paste this key into `application.properties`.** Unlike the others, this one is read from the
environment:

```properties
spring.ai.openai.api-key=${OPENAI_API_KEY}
```

Set the environment variable instead.

**Windows:**

```bash
setx OPENAI_API_KEY "sk-your-key-here"
```

**macOS and Linux:**

```bash
export OPENAI_API_KEY="sk-your-key-here"
```

Add the export line to `~/.zshrc` or `~/.bashrc` so it survives a restart.

**Then restart your IDE completely.** IntelliJ reads environment variables when it launches, so a key
set after IntelliJ opened is invisible to it. This catches almost everyone once.

**About cost.** The whole course session runs on well under a dollar. Chat calls are a fraction of a
cent each. Image generation is the expensive one at a few cents per image, so generate two or three
during the demo rather than twenty.

### 2.2 Mailtrap, for the verification code

When someone signs up, the app emails them a six digit code. Mailtrap catches that email so it never
reaches a real inbox, which is exactly what you want while testing.

1. Go to **mailtrap.io** and create a free account.
2. In the left menu open **Email Testing**, then **Inboxes**.
3. Click your inbox, then the **SMTP Settings** tab.
4. In the **Integrations** dropdown choose **Java**, or just read the credentials off the page.
5. Copy the **Username** and **Password**. They look like random strings, not like an email address.

Paste them into `E-Commerce-Backend/src/main/resources/application.properties`:

```properties
spring.mail.host=sandbox.smtp.mailtrap.io
spring.mail.port=587
spring.mail.username=YOUR_MAILTRAP_USERNAME
spring.mail.password=YOUR_MAILTRAP_PASSWORD
```

**Where the codes arrive.** Not in your email. Go back to Mailtrap, open the same inbox, and the
newest message at the top is the verification email. Open it and read the code from there.

The code is six digits, valid for ten minutes, usable once, and you get five attempts. There is a one
minute wait before you can ask for a new one.

### 2.3 Cloudinary, for product images

Product photos are uploaded to Cloudinary rather than stored on your machine. This matters more here
than in the plain app, because generated images go through the same upload path.

1. Go to **cloudinary.com** and sign up for the free plan.
2. You land on the **Dashboard**. Everything you need is in the box at the top.
3. Copy the **Cloud name**, **API Key** and **API Secret**. Click the eye icon to reveal the secret.

```properties
cloudinary.cloud-name=YOUR_CLOUD_NAME
cloudinary.api-key=YOUR_API_KEY
cloudinary.api-secret=YOUR_API_SECRET
cloudinary.folder=ecommerce
```

### 2.4 Razorpay, for payments

Only needed for the online payment flow. Cash on delivery works without it.

1. Go to **razorpay.com** and create an account.
2. Switch to **Test Mode** using the toggle at the top.
3. Open **Settings**, then **API Keys**, then **Generate Test Key**.
4. Copy the **Key Id** and the **Key Secret**. The secret is shown once.

```properties
razorpay.api.key=YOUR_RAZORPAY_KEY_ID
razorpay.api.secret=YOUR_RAZORPAY_KEY_SECRET
```

### 2.5 GitHub OAuth, optional

Only for the "Sign in with GitHub" button.

1. On GitHub open **Settings**, then **Developer settings**, then **OAuth Apps**, then
   **New OAuth App**.
2. Homepage URL: `http://localhost:8080`
3. Authorization callback URL: `http://localhost:8080/login/oauth2/code/github`
4. Create it, generate a client secret, and copy both values.

```properties
spring.security.oauth2.client.registration.github.client-id=${GITHUB_CLIENT_ID:YOUR_CLIENT_ID}
spring.security.oauth2.client.registration.github.client-secret=${GITHUB_CLIENT_SECRET:YOUR_CLIENT_SECRET}
```

Do not leave these blank. Spring Boot refuses to start with an empty client id.

---

## Step 3: Run the backend

Docker Desktop open, then from the `E-Commerce-Backend` folder:

```bash
mvn spring-boot:run
```

Or run `ECommerceBackendApplication` in IntelliJ.

The first start does three things you can watch go past in the log:

1. Docker Compose starts the pgvector container
2. Hibernate creates every table
3. The vector table is set up, and you see a line from `PgVectorStore`

You know it worked when you see both of these:

```
Container e-commerce-backend-pgvector-1  Healthy
o.s.a.v.pgvector.PgVectorStore : Initializing PGVectorStore schema for table: vector_store
Started ECommerceBackendApplication
```

If the app starts but you never see the `PgVectorStore` line, the AI half is not wired up and search
will fail later.

---

## Step 4: Run the frontend

Open a **second terminal**. Leave the backend running in the first.

From the `ecom-ui` folder:

```bash
npm i
```

This downloads the dependencies and creates `node_modules`. It takes a minute or two the first time.
You only run it again if `package.json` changes.

Then:

```bash
npm run dev
```

```
VITE ready in 400 ms
Local: http://localhost:5173/
```

Open **http://localhost:5173**.

**Nothing to configure in the frontend.** It calls `/api` and Vite forwards that to the backend on
8080, which is already set in `vite.config.js`. The browser only ever talks to one address, so there
is no CORS problem to deal with.

---

## Step 5: Create your first admin

The admin panel is locked to admins, and the endpoint that grants the admin role is itself admin only,
so the first one has to be made in the database.

1. Register at **http://localhost:5173** with any email.
2. Open Mailtrap, read the six digit code from the newest message, and enter it.
3. Run this, using the email you signed up with:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';
```

4. Sign out and sign back in. The role is carried in the token, so it only applies from a fresh login.

You can connect to the database on `localhost:5432` with user `postgres`, password `telusko`, database
`telusko`. Those come from the compose file.

---

## Step 6: Add products, and understand why the order matters

In the admin panel:

1. **Categories**, create two or three.
2. **Catalog**, add four or five products across those categories.

**Products are indexed for AI search at the moment they are saved.** There is no background job. If a
product was in the database before the AI features existed, it is not in the vector store, and search
will not find it. Open it in the admin panel and save it again to fix that.

While adding a product, try the two **Generate with AI** buttons. One writes the description, the other
produces the photo.

---

## Step 7: See the AI working

Quick tour, in the order that shows the most in the least time:

| Do this | Where |
|---|---|
| Search `something for my desk` | Header search bar |
| Ask `is this good for beginners?` | Any product page |
| Place an order, then reload the home page | Home page, "Inspired by your purchases" |
| Add items to the cart | Cart page, "Goes well with your cart" |
| Open the assistant and ask `where is my last order?` | Floating button, bottom corner |
| Raise a support ticket in messy language | Support page, then check the admin Support tab |
| Ask `give me a sales summary for this week` | Admin panel, Analytics tab |

The search one is the best first demo. Type a sentence that contains none of the words in any product
name, and real products still come back.

---

## Optional: Prometheus and Grafana

The backend publishes metrics about every AI call: how long each feature takes, how many tokens it
used, and how often retrieval came back empty.

From the `E-Commerce-Backend/monitoring` folder, with the app already running:

```bash
docker compose up -d
```

| Tool | Address | Login |
|---|---|---|
| Prometheus | http://localhost:9090 | none |
| Grafana | http://localhost:3000 | admin / admin |

In Grafana open **Dashboards**, then **E-Commerce**, then **E-Commerce AI Monitoring**. Set the time
range to **Last 15 minutes** and refresh to **10s**, because the defaults make a live demo look empty.

Now use the shop in another window and watch the panels move.

To stop it:

```bash
docker compose down
```

This is entirely optional. The application works without it.

---

## Everyday commands

```bash
# Terminal 1, in E-Commerce-Backend
mvn spring-boot:run
```

```bash
# Terminal 2, in ecom-ui
npm run dev
```

```bash
# Optional, in E-Commerce-Backend/monitoring
docker compose up -d
```

`npm i` is only needed the first time, or after someone changes `package.json`.

---

## When something does not work

| What you see | What it usually is |
|---|---|
| Docker error at startup | Docker Desktop is not running. Open it and try again |
| `Incorrect API key provided` | The key is wrong, or has a stray space, or was pasted in quotes |
| `You exceeded your current quota` | The OpenAI account has no credit. Add some in Billing |
| Key is set but the app cannot see it | The IDE was open when you set it. Restart the IDE completely |
| `could not open extension control file "vector"` | An old plain Postgres container is still running. Run `docker compose down`, then start again |
| Search returns nothing at all | The products were saved before indexing existed. Open each one in the admin panel and save it again |
| Search works, assistant returns 401 | You are signed out. Every AI route except search needs a signed in user |
| Analytics assistant returns 403 | You are signed in as a customer, not an admin |
| Image generation fails | `gpt-image-1` is not enabled on every account. Check your OpenAI account has image access |
| Grafana panels say "No data" | Use an AI feature first. Meters register on first use, not at startup |
| `Port 8080 is already in use` | An older run is still going. Stop it, or change `server.port` |

---

## Project layout

Two folders, one app.

```
With AI/
  E-Commerce-Backend/               the API
    src/main/java/com/telusko/
      controller/                   REST endpoints
      service/                      business logic, including the AI services
      tools/                        methods the model can call directly
      repository/                   database queries
      model/                        JPA entities
      dto/                          request and response shapes
      security/                     JWT, OAuth2, SecurityConfig
      config/                       chat clients, Cloudinary, mail, metrics
    src/main/resources/
      application.properties        every setting and key lives here
      init/schema.sql               the vector table
      prompts/                      prompt templates kept out of the Java
    monitoring/                     Prometheus and Grafana, optional
    docker-compose.yml              the pgvector database

  ecom-ui/                          the storefront
    src/
      api.js                        every backend call, plus JWT storage and refresh
      store.jsx                     session and cart state
      ui.jsx                        shared pieces: Money, Thumb, badges, skeletons
      styles.css                    design tokens and component styles
      components/                   Header, ProductCard, AiAssistant
      pages/                        one file per route
      pages/admin/                  admin console tabs
```

The `tools/` package is the only folder here that does not exist in the plain version. Those are
methods the model is allowed to call on its own, which is how the analytics and ticket assistants read
real numbers and change real records.

---

## Worth knowing about how it works

**The cart lives on the server.** Every change returns the recalculated cart and that becomes the new
state in the browser. Totals, discounts and stock rules are never worked out twice.

**Tokens refresh by themselves.** An access token lasts 30 minutes. When a call comes back 401,
`api.js` swaps the refresh token for a new pair and replays the request once. If several calls fail at
the same moment they share one refresh rather than firing several.

**Products are indexed as they are saved.** There is no sync job and no schedule. Saving a product
writes it to the database and to the vector store in the same call, which is why a product added
before the AI existed has to be saved once more to become searchable.

**The database is still the source of truth.** The vector store decides which products are relevant.
Their price, stock and availability are always read back from Postgres, so a withdrawn product
disappears from results even while the vector store still remembers it.

**Prompts and customer data stay out of the metrics.** Model latency and token counts are published,
the text of prompts and replies is not. Those contain names, addresses and order contents, and
monitoring systems are usually readable by the whole team.

---

## A note on the credentials in this file

The `application.properties` in this repository ships with working keys so the project runs out of the
box during the course. They are shared, rate limited, and can stop working at any time.

Create your own before you rely on the app for anything. The OpenAI key in particular should always be
your own, because it is tied to a billing account.

If you push this project anywhere public, replace the keys first. Credentials in a properties file are
credentials in your git history, and deleting them later does not remove them from the history.

---

## The plain version

If you want to see what this app looked like before the AI, it is the sibling folder. Same
features, same screens, no AI anywhere.

Open `Without AI/README.md`.
