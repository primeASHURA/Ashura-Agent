# Web engineering

Build and reverse web apps with the same toolset.

## Building

- Read `package.json` (scripts, deps), then the entry (`src/main.*`, `pages/`, `app/`).
- Routing, data fetching, and state each get one home; do not spread them.
- Every network call: timeout, error branch, loading branch, and a retry rule.
- Forms: label every field, validate on submit, show field-level errors.
- Performance budget: no blocking font/script in the head, images sized, no layout shift.
- Deployable default: static build output plus a documented start command.

## Reverse engineering a page

1. Fetch the document (`fetch_url`), record title, meta, script `src` list and inline sizes.
2. `search_text` the saved HTML/JS for `fetch(`, `axios`, `/api/`, `graphql`, `wss://`, `token`,
   `authorization`, `apikey`, `firebase`, `supabase`.
3. Bundled output: look for `webpackChunk`, `__vite__`, `assets/*.js`; minified code keeps string
   literals intact, so grep still finds routes and verbs.
4. Rebuild the request: method, path, headers, body fields, auth source (cookie, localStorage,
   header). Name the mechanism, not "it calls an API".
5. Only then change something: userscript, request rewrite, or UI patch.

## Userscript delivery

```
// ==UserScript==
// @name         target-thing
// @namespace    local
// @version      1.0.0
// @match        https://exact.host/path/*
// @run-at       document-start
// @grant        none
// ==/UserScript==
```

Narrow `@match`. Tear down listeners. Reload the page and confirm the change survives.

## Defence line

Client checks are decoration; move the rule to the server and validate it there too.
