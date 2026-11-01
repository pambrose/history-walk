// The Kotlin/JS plugin adds this file to the Karma config that jsBrowserTest runs (the client specs in headless Chrome).
//
// Kotest runs every spec inside one Mocha test and reports the results only once all of them have run, so Karma hears
// nothing from the browser for the whole run. Karma gives up on a browser after 30 silent seconds by default, which a
// longer suite on a slow CI runner could reach. A test that hangs still fails, named, after its own timeout
// (CLIENT_TEST_TIMEOUT_MS in src/jsTest), well within this.
config.set({
  browserNoActivityTimeout: 120000,
});
