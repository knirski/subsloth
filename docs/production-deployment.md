# Production Deployment

## Cross-Origin Isolation Headers

The web app uses `@sqlite.org/sqlite-wasm` with Origin Private File System (OPFS)
for persistent database storage. OPFS requires the page to be cross-origin isolated,
which means the server must send these HTTP headers:

```
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Embedder-Policy: require-corp
```

Without these headers, `sqlite3.oo1.OpfsDb` fails silently and the database falls
back to in-memory storage — data is lost on every page reload.

### Nginx

```nginx
location / {
    add_header Cross-Origin-Opener-Policy "same-origin";
    add_header Cross-Origin-Embedder-Policy "require-corp";
    # existing proxy/static config...
}
```

### Netlify

Create `webApp/public/_headers` (or equivalent in the publish directory):

```
/*
  Cross-Origin-Opener-Policy: same-origin
  Cross-Origin-Embedder-Policy: require-corp
```

### Vercel

Create `vercel.json` in the project root:

```json
{
  "headers": [
    {
      "source": "/(.*)",
      "headers": [
        { "key": "Cross-Origin-Opener-Policy", "value": "same-origin" },
        { "key": "Cross-Origin-Embedder-Policy", "value": "require-corp" }
      ]
    }
  ]
}
```

### Caddy

```
header {
    Cross-Origin-Opener-Policy "same-origin"
    Cross-Origin-Embedder-Policy "require-corp"
}
```

### Apache (.htaccess)

```
<IfModule mod_headers.c>
    Header always set Cross-Origin-Opener-Policy "same-origin"
    Header always set Cross-Origin-Embedder-Policy "require-corp"
</IfModule>
```

## Wasm Media Types

Ensure the server serves `.wasm` files with the correct MIME type:

```
application/wasm
```

## SPA Fallback

The web app is a single-page application. Configure your server to serve
`index.html` for all routes that don't match a static file:

### Nginx

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

### Netlify

Create `webApp/public/_redirects`:

```
/*    /index.html    200
```
