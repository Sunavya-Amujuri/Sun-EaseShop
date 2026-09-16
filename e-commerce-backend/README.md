# E-Commerce Backend (no AI)

A complete e-commerce backend on Spring Boot 3.5.16 and Java 17, with **no AI in it**. This is
the project we add Spring AI to in class, so everything the AI features will later hook into is
already here and working.

## Running

Needs a PostgreSQL running on this machine. Create the database once:

```sql
CREATE DATABASE telusko;
```

Then check that the url, username and password at the top of `application.properties` match your
own Postgres, and start the app:

```bash
mvn spring-boot:run
```

Tables are created by Hibernate (`ddl-auto=update`), so there is no schema to import.

- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

Cloudinary, Mailtrap, Razorpay and GitHub OAuth credentials live in `application.properties` and
can be overridden with environment variables. The app starts and the catalog works whatever they
are set to; product image upload, verification emails and online payment need real keys.

## What is in it

| Area | Endpoints |
|---|---|
| Auth | register, login, logout, refresh token, email verification by OTP, forgot and reset password by OTP, change password, assign role |
| Catalog | products list, product by id, products by category, keyword search, create, update, soft delete |
| Categories | list, by id, create, update, delete |
| Cart | view, set item quantity, remove item, clear |
| Coupons | admin CRUD and activate, customer list, apply, remove |
| Orders | place, my orders, order detail, cancel, admin list, admin status update |
| Payments | create Razorpay order, verify signature, mark failed, cancel |
| Addresses | list, add, update, delete, set default |
| Support | create ticket, my tickets, messages, admin queue, admin reply, admin status update |

Security is JWT: a 30 minute access token and a 7 day refresh token, BCrypt passwords, and
role-based rules in `SecurityConfig`. GitHub OAuth2 login is wired but optional.

## Search is deliberately keyword based

`GET /api/v1/ecommerce/products/search?query=...` runs a case insensitive SQL `like` over
product name, description and category name:

```java
// ProductRepository
@Query("select p from Product p left join fetch p.category c "
     + "where p.active = true and ("
     + "  lower(p.name) like lower(concat('%', :q, '%')) ...")
List<Product> searchActive(@Param("q") String q);
```

So `mouse` finds the Mouse, and `something for my desk` finds nothing, because no product row
contains those words. That gap is the reason semantic search is worth adding, and this method is
the one it replaces.

## What is not here, on purpose

No Spring AI dependency, no vector store, no embeddings, no chat client, no prompt files. The
platform versions match the AI version of this project exactly, so integrating Spring AI later
means adding dependencies rather than upgrading anything underneath them.
