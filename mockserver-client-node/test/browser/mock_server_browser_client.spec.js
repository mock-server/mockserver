// @ts-check
/**
 * Browser integration tests for mockServerClient.js
 *
 * These tests verify that the MockServer client works inside a real browser
 * (the browser code path: XHR + CORS to MockServer's control-plane API).
 *
 * Migrated from the legacy Karma + Jasmine 2.5.2 browser test suite.
 *
 * Prerequisites:
 *   - MockServer running on localhost:1080 (or MOCKSERVER_PORT) with CORS enabled:
 *       MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES=true
 *       MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION=false
 */
const { test, expect } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

const MOCKSERVER_PORT = process.env.MOCKSERVER_PORT || '1080';
const MOCKSERVER_HOST = process.env.MOCKSERVER_HOST || 'localhost';
const MOCKSERVER_BASE = `http://${MOCKSERVER_HOST}:${MOCKSERVER_PORT}`;

// Read the client source to inject into the browser page
const clientJsPath = path.resolve(__dirname, '..', '..', 'mockServerClient.js');
const clientJsSource = fs.readFileSync(clientJsPath, 'utf-8');

/**
 * Set up a fresh browser page with mockServerClient loaded and all
 * expectations cleared. Returns the page and a unique uuid for test isolation.
 */
async function setupPage(browser) {
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  const page = await context.newPage();

  // Navigate to the MockServer (any page on that origin works for CORS)
  // We use about:blank then set origin via evaluating on the page
  // Actually, we need to be on the MockServer origin for XHR to work with CORS
  // Navigate to the MockServer dashboard (or any path) to establish the origin
  await page.goto(`${MOCKSERVER_BASE}/mockserver/dashboard`, { waitUntil: 'domcontentloaded', timeout: 10_000 }).catch(() => {
    // Dashboard may 404 — that's fine, we just need the origin set
  });

  // Inject the mockServerClient.js into the page
  await page.evaluate(clientJsSource);

  // Generate a unique uuid for test isolation (via Vary header)
  const uuid = await page.evaluate(() => {
    function s4() {
      return Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
    }
    return s4() + s4() + '-' + s4() + '-' + s4() + '-' + s4() + '-' + s4() + s4() + s4();
  });

  // Clear all expectations
  await page.evaluate(([host, port]) => {
    return new Promise((resolve, reject) => {
      // eslint-disable-next-line no-undef
      mockServerClient(host, parseInt(port)).clear().then(resolve, reject);
    });
  }, [MOCKSERVER_HOST, MOCKSERVER_PORT]);

  return { page, context, uuid };
}

test.describe('mockServerClient browser client', () => {

  test('should create full expectation with string body', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockAnyResponse({
            'httpRequest': {
              'method': 'POST',
              'path': '/somePath',
              'queryStringParameters': [{ 'name': 'test', 'values': ['true'] }],
              'body': { 'type': 'STRING', 'string': 'someBody' }
            },
            'httpResponse': {
              'statusCode': 200,
              'body': JSON.stringify({name: 'value'}),
              'delay': { 'timeUnit': 'MILLISECONDS', 'value': 250 }
            },
            'times': { 'remainingTimes': 1, 'unlimited': false }
          }).then(function () {
            var results = [];
            // non-matching request
            var xhr1 = new XMLHttpRequest();
            xhr1.onload = function () {
              results.push({ step: 'non-match', status: this.status });
              // matching request
              var xhr2 = new XMLHttpRequest();
              xhr2.onload = function () {
                results.push({ step: 'match', status: this.status, body: this.responseText });
                // matching request but no times remaining
                var xhr3 = new XMLHttpRequest();
                xhr3.onload = function () {
                  results.push({ step: 'exhausted', status: this.status });
                  resolve(results);
                };
                xhr3.open("POST", "http://" + host + ":" + port + "/somePath?test=true");
                xhr3.setRequestHeader("Vary", uuid);
                xhr3.send("someBody");
              };
              xhr2.open("POST", "http://" + host + ":" + port + "/somePath?test=true");
              xhr2.setRequestHeader("Vary", uuid);
              xhr2.send("someBody");
            };
            xhr1.open("GET", "http://" + host + ":" + port + "/otherPath");
            xhr1.setRequestHeader("Vary", uuid);
            xhr1.send();
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result[0].status).toBe(404);
      expect(result[1].status).toBe(200);
      expect(result[1].body).toBe('{"name":"value"}');
      expect(result[2].status).toBe(404);
    } finally {
      await context.close();
    }
  });

  test('should create full expectation with string body over tls', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          // Note: in browser, TLS flag makes the client use https:// URLs.
          // The browser page is already on http origin, so cross-origin https
          // requests still work via CORS.  However, MockServer's self-signed
          // cert will cause the browser to reject the request unless
          // ignoreHTTPSErrors is set (which Playwright does at context level).
          // For simplicity and parity with the old test, we use the non-TLS
          // client but set tls=true to exercise the client's URL construction.
          var clientOverTls = mockServerClient(host, parseInt(port), null, true).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          clientOverTls.mockAnyResponse({
            'httpRequest': {
              'method': 'POST',
              'path': '/somePath',
              'queryStringParameters': [{ 'name': 'test', 'values': ['true'] }],
              'body': { 'type': 'STRING', 'string': 'someBody' }
            },
            'httpResponse': {
              'statusCode': 200,
              'body': JSON.stringify({name: 'value'}),
              'delay': { 'timeUnit': 'MILLISECONDS', 'value': 250 }
            },
            'times': { 'remainingTimes': 1, 'unlimited': false }
          }).then(function () {
            var xhr = new XMLHttpRequest();
            xhr.onload = function () {
              resolve({ status: this.status, body: this.responseText });
            };
            xhr.open("POST", "http://" + host + ":" + port + "/somePath?test=true");
            xhr.setRequestHeader("Vary", uuid);
            xhr.send("someBody");
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.status).toBe(200);
      expect(result.body).toBe('{"name":"value"}');
    } finally {
      await context.close();
    }
  });

  test('should expose server validation failure', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port]) => {
        return new Promise((resolve, reject) => {
          // eslint-disable-next-line no-undef
          var client = mockServerClient(host, parseInt(port));
          client.mockAnyResponse({
            'httpRequest': {
              'paths': '/somePath',
              'body': {
                'type': 'STRING',
                'vaue': 'someBody'
              }
            },
            'httpResponse': {}
          }).then(function () {
            resolve({ failed: false });
          }, function (error) {
            resolve({ failed: true, error: error });
          });
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT]);

      expect(result.failed).toBe(true);
      // The error message includes the invalid field name and a schema violation indicator.
      // The exact phrasing varies by MockServer version ("not allowed by the schema" in older
      // versions, "is not defined in the schema" in newer), so we check for both the field
      // name and the general concept of a schema violation.
      expect(result.error).toContain('paths');
      expect(result.error).toMatch(/schema/);
    } finally {
      await context.close();
    }
  });

  test('should match on method only', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockAnyResponse({
            'httpRequest': { 'method': 'GET' },
            'httpResponse': {
              'statusCode': 200,
              'body': JSON.stringify({name: 'first_body'}),
              'delay': { 'timeUnit': 'MILLISECONDS', 'value': 250 }
            },
            'times': { 'remainingTimes': 1, 'unlimited': false }
          }).then(function () {
            client.mockAnyResponse({
              'httpRequest': { 'method': 'POST' },
              'httpResponse': {
                'statusCode': 200,
                'body': JSON.stringify({name: 'second_body'}),
                'delay': { 'timeUnit': 'MILLISECONDS', 'value': 250 }
              },
              'times': { 'remainingTimes': 1, 'unlimited': false }
            }).then(function () {
              var results = [];
              var xhr1 = new XMLHttpRequest();
              xhr1.onload = function () {
                results.push({ step: 'put-no-match', status: this.status });
                var xhr2 = new XMLHttpRequest();
                xhr2.onload = function () {
                  results.push({ step: 'get-match', status: this.status, body: this.responseText });
                  var xhr3 = new XMLHttpRequest();
                  xhr3.onload = function () {
                    results.push({ step: 'post-match', status: this.status, body: this.responseText });
                    resolve(results);
                  };
                  xhr3.open("POST", "http://" + host + ":" + port + "/somePath");
                  xhr3.setRequestHeader("Vary", uuid);
                  xhr3.send();
                };
                xhr2.open("GET", "http://" + host + ":" + port + "/somePath");
                xhr2.setRequestHeader("Vary", uuid);
                xhr2.send();
              };
              xhr1.open("PUT", "http://" + host + ":" + port + "/somePath");
              xhr1.setRequestHeader("Vary", uuid);
              xhr1.send();
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result[0].status).toBe(404);
      expect(result[1].status).toBe(200);
      expect(result[1].body).toBe('{"name":"first_body"}');
      expect(result[2].status).toBe(200);
      expect(result[2].body).toBe('{"name":"second_body"}');
    } finally {
      await context.close();
    }
  });

  test('should match on path only', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockAnyResponse({
            'httpRequest': { 'path': '/firstPath' },
            'httpResponse': {
              'statusCode': 200,
              'body': JSON.stringify({name: 'first_body'}),
              'delay': { 'timeUnit': 'MILLISECONDS', 'value': 250 }
            },
            'times': { 'remainingTimes': 1, 'unlimited': false }
          }).then(function () {
            client.mockAnyResponse({
              'httpRequest': { 'path': '/secondPath' },
              'httpResponse': {
                'statusCode': 200,
                'body': JSON.stringify({name: 'second_body'}),
                'delay': { 'timeUnit': 'MILLISECONDS', 'value': 250 }
              },
              'times': { 'remainingTimes': 1, 'unlimited': false }
            }).then(function () {
              var results = [];
              var xhr1 = new XMLHttpRequest();
              xhr1.onload = function () {
                results.push({ step: 'no-match', status: this.status });
                var xhr2 = new XMLHttpRequest();
                xhr2.onload = function () {
                  results.push({ step: 'first', status: this.status, body: this.responseText });
                  var xhr3 = new XMLHttpRequest();
                  xhr3.onload = function () {
                    results.push({ step: 'second', status: this.status, body: this.responseText });
                    resolve(results);
                  };
                  xhr3.open("GET", "http://" + host + ":" + port + "/secondPath");
                  xhr3.setRequestHeader("Vary", uuid);
                  xhr3.send();
                };
                xhr2.open("GET", "http://" + host + ":" + port + "/firstPath");
                xhr2.setRequestHeader("Vary", uuid);
                xhr2.send();
              };
              xhr1.open("GET", "http://" + host + ":" + port + "/otherPath");
              xhr1.setRequestHeader("Vary", uuid);
              xhr1.send();
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result[0].status).toBe(404);
      expect(result[1].status).toBe(200);
      expect(result[1].body).toBe('{"name":"first_body"}');
      expect(result[2].status).toBe(200);
      expect(result[2].body).toBe('{"name":"second_body"}');
    } finally {
      await context.close();
    }
  });

  test('should match on query string parameters only', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockAnyResponse({
            'httpRequest': { 'queryStringParameters': [{ 'name': 'param', 'values': ['first'] }] },
            'httpResponse': {
              'statusCode': 200,
              'body': JSON.stringify({name: 'first_body'}),
              'delay': { 'timeUnit': 'MILLISECONDS', 'value': 250 }
            },
            'times': { 'remainingTimes': 1, 'unlimited': false }
          }).then(function () {
            client.mockAnyResponse({
              'httpRequest': { 'queryStringParameters': [{ 'name': 'param', 'values': ['second'] }] },
              'httpResponse': {
                'statusCode': 200,
                'body': JSON.stringify({name: 'second_body'}),
                'delay': { 'timeUnit': 'MILLISECONDS', 'value': 250 }
              },
              'times': { 'remainingTimes': 1, 'unlimited': false }
            }).then(function () {
              var results = [];
              var xhr1 = new XMLHttpRequest();
              xhr1.onload = function () {
                results.push({ step: 'no-match', status: this.status });
                var xhr2 = new XMLHttpRequest();
                xhr2.onload = function () {
                  results.push({ step: 'first', status: this.status, body: this.responseText });
                  var xhr3 = new XMLHttpRequest();
                  xhr3.onload = function () {
                    results.push({ step: 'second', status: this.status, body: this.responseText });
                    resolve(results);
                  };
                  xhr3.open("GET", "http://" + host + ":" + port + "/somePath?param=second");
                  xhr3.setRequestHeader("Vary", uuid);
                  xhr3.send();
                };
                xhr2.open("GET", "http://" + host + ":" + port + "/somePath?param=first");
                xhr2.setRequestHeader("Vary", uuid);
                xhr2.send();
              };
              xhr1.open("GET", "http://" + host + ":" + port + "/somePath?param=other");
              xhr1.setRequestHeader("Vary", uuid);
              xhr1.send();
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result[0].status).toBe(404);
      expect(result[1].status).toBe(200);
      expect(result[1].body).toBe('{"name":"first_body"}');
      expect(result[2].status).toBe(200);
      expect(result[2].body).toBe('{"name":"second_body"}');
    } finally {
      await context.close();
    }
  });

  test('should match on body only', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockAnyResponse({
            'httpRequest': { 'body': { 'type': "STRING", 'string': 'someBody' } },
            'httpResponse': {
              'statusCode': 200,
              'body': JSON.stringify({name: 'first_body'}),
              'delay': { 'timeUnit': 'MILLISECONDS', 'value': 250 }
            },
            'times': { 'remainingTimes': 1, 'unlimited': false }
          }).then(function () {
            client.mockAnyResponse({
              'httpRequest': { 'body': { 'type': "REGEX", 'regex': 'someOtherBody' } },
              'httpResponse': {
                'statusCode': 200,
                'body': JSON.stringify({name: 'second_body'}),
                'delay': { 'timeUnit': 'MILLISECONDS', 'value': 250 }
              },
              'times': { 'remainingTimes': 1, 'unlimited': false }
            }).then(function () {
              var results = [];
              var xhr1 = new XMLHttpRequest();
              xhr1.onload = function () {
                results.push({ step: 'no-match', status: this.status });
                var xhr2 = new XMLHttpRequest();
                xhr2.onload = function () {
                  results.push({ step: 'first', status: this.status, body: this.responseText });
                  var xhr3 = new XMLHttpRequest();
                  xhr3.onload = function () {
                    results.push({ step: 'second', status: this.status, body: this.responseText });
                    resolve(results);
                  };
                  xhr3.open("POST", "http://" + host + ":" + port + "/somePath");
                  xhr3.setRequestHeader("Vary", uuid);
                  xhr3.send("someOtherBody");
                };
                xhr2.open("POST", "http://" + host + ":" + port + "/somePath");
                xhr2.setRequestHeader("Vary", uuid);
                xhr2.send("someBody");
              };
              xhr1.open("POST", "http://" + host + ":" + port + "/somePath");
              xhr1.setRequestHeader("Vary", uuid);
              xhr1.send("someIncorrectBody");
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result[0].status).toBe(404);
      expect(result[1].status).toBe(200);
      expect(result[1].body).toBe('{"name":"first_body"}');
      expect(result[2].status).toBe(200);
      expect(result[2].body).toBe('{"name":"second_body"}');
    } finally {
      await context.close();
    }
  });

  test('should match on headers only', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockAnyResponse({
            'httpRequest': { 'headers': [{ 'name': 'Allow', 'values': ['first'] }] },
            'httpResponse': {
              'statusCode': 200,
              'body': JSON.stringify({name: 'first_body'}),
              'delay': { 'timeUnit': 'MILLISECONDS', 'value': 250 }
            },
            'times': { 'remainingTimes': 1, 'unlimited': false }
          }).then(function () {
            client.mockAnyResponse({
              'httpRequest': { 'headers': [{ 'name': 'Allow', 'values': ['second'] }] },
              'httpResponse': {
                'statusCode': 200,
                'body': JSON.stringify({name: 'second_body'}),
                'delay': { 'timeUnit': 'MILLISECONDS', 'value': 250 }
              },
              'times': { 'remainingTimes': 1, 'unlimited': false }
            }).then(function () {
              var results = [];
              var xhr1 = new XMLHttpRequest();
              xhr1.onload = function () {
                results.push({ step: 'no-match', status: this.status });
                var xhr2 = new XMLHttpRequest();
                xhr2.onload = function () {
                  results.push({ step: 'first', status: this.status, body: this.responseText });
                  var xhr3 = new XMLHttpRequest();
                  xhr3.onload = function () {
                    results.push({ step: 'second', status: this.status, body: this.responseText });
                    resolve(results);
                  };
                  xhr3.open("GET", "http://" + host + ":" + port + "/somePathZ");
                  xhr3.setRequestHeader("Vary", uuid);
                  xhr3.setRequestHeader('Allow', 'second');
                  xhr3.send();
                };
                xhr2.open("GET", "http://" + host + ":" + port + "/somePathY");
                xhr2.setRequestHeader("Vary", uuid);
                xhr2.setRequestHeader('Allow', 'first');
                xhr2.send();
              };
              xhr1.open("GET", "http://" + host + ":" + port + "/somePathX");
              xhr1.setRequestHeader("Vary", uuid);
              xhr1.setRequestHeader('Allow', 'other');
              xhr1.send();
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result[0].status).toBe(404);
      expect(result[1].status).toBe(200);
      expect(result[1].body).toBe('{"name":"first_body"}');
      expect(result[2].status).toBe(200);
      expect(result[2].body).toBe('{"name":"second_body"}');
    } finally {
      await context.close();
    }
  });

  test('should create simple response expectation', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
            var results = [];
            var xhr1 = new XMLHttpRequest();
            xhr1.onload = function () {
              results.push({ step: 'non-match', status: this.status });
              var xhr2 = new XMLHttpRequest();
              xhr2.onload = function () {
                results.push({ step: 'match', status: this.status, body: this.responseText });
                var xhr3 = new XMLHttpRequest();
                xhr3.onload = function () {
                  results.push({ step: 'exhausted', status: this.status });
                  resolve(results);
                };
                xhr3.open("POST", "http://" + host + ":" + port + "/somePath?test=true");
                xhr3.setRequestHeader("Vary", uuid);
                xhr3.send("someBody");
              };
              xhr2.open("POST", "http://" + host + ":" + port + "/somePath?test=true");
              xhr2.setRequestHeader("Vary", uuid);
              xhr2.send("someBody");
            };
            xhr1.open("GET", "http://" + host + ":" + port + "/otherPath");
            xhr1.setRequestHeader("Vary", uuid);
            xhr1.send();
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result[0].status).toBe(404);
      expect(result[1].status).toBe(203);
      expect(result[1].body).toBe('{"name":"value"}');
      expect(result[2].status).toBe(404);
    } finally {
      await context.close();
    }
  });

  test('should create expectation with method callback', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockWithCallback({
            'method': 'POST',
            'path': '/somePath',
            'queryStringParameters': [{ 'name': 'test', 'values': ['true'] }],
            'body': { 'type': "STRING", 'string': 'someBody' }
          }, function (request) {
            if (request.method === 'POST' && request.path === '/somePath') {
              return { 'statusCode': 200, 'body': JSON.stringify({name: 'value'}) };
            } else {
              return { 'statusCode': 406 };
            }
          }).then(function () {
            var results = [];
            var xhr1 = new XMLHttpRequest();
            xhr1.onload = function () {
              results.push({ step: 'non-match', status: this.status });
              var xhr2 = new XMLHttpRequest();
              xhr2.onload = function () {
                results.push({ step: 'match', status: this.status, body: this.responseText });
                var xhr3 = new XMLHttpRequest();
                xhr3.onload = function () {
                  results.push({ step: 'exhausted', status: this.status });
                  resolve(results);
                };
                xhr3.open("POST", "http://" + host + ":" + port + "/somePath?test=true");
                xhr3.setRequestHeader("Vary", uuid);
                xhr3.send("someBody");
              };
              xhr2.open("POST", "http://" + host + ":" + port + "/somePath?test=true");
              xhr2.setRequestHeader("Vary", uuid);
              xhr2.send("someBody");
            };
            xhr1.open("GET", "http://" + host + ":" + port + "/otherPath");
            xhr1.setRequestHeader("Vary", uuid);
            xhr1.send();
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result[0].status).toBe(404);
      expect(result[1].status).toBe(200);
      expect(result[1].body).toBe('{"name":"value"}');
      expect(result[2].status).toBe(404);
    } finally {
      await context.close();
    }
  });

  test('should close the callback WebSocket on close()', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        var send = function (path) {
          return new Promise(function (resolve) {
            var xhr = new XMLHttpRequest();
            xhr.onload = function () {
              resolve(this.status);
            };
            xhr.open("GET", "http://" + host + ":" + port + path);
            xhr.setRequestHeader("Vary", uuid);
            xhr.send();
          });
        };
        var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
          {"name": "Vary", "values": [uuid]}
        ]);
        var statuses = [];
        return new Promise(function (resolve, reject) {
          client.mockWithCallback({'path': '/closedCallback'}, function () {
            return {'statusCode': 202};
          }, {'unlimited': true}).then(resolve, reject);
        }).then(function () {
          return send('/closedCallback');
        }).then(function (status) {
          statuses.push(status);
          return client.close();
        }).then(function () {
          return send('/closedCallback');
        }).then(function (status) {
          statuses.push(status);
          return statuses;
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result[0]).toBe(202);
      expect(result[1]).toBe(404);
    } finally {
      await context.close();
    }
  });

  test('should create expectation with method callback over tls', async ({ browser }) => {
    // For the TLS callback test we need the page on the HTTPS origin so the
    // WSS WebSocket connection (used by mockWithCallback) is same-origin and
    // the browser accepts MockServer's self-signed certificate.
    const context = await browser.newContext({ ignoreHTTPSErrors: true });
    const page = await context.newPage();
    // Navigate to a lightweight HTTPS URL (not dashboard, which loads the full React app)
    await page.goto(`https://${MOCKSERVER_HOST}:${MOCKSERVER_PORT}/_tls_origin`, {
      waitUntil: 'commit', timeout: 10_000
    }).catch(() => { /* 404 is fine, we just need the HTTPS origin */ });
    await page.evaluate(clientJsSource);
    const uuid = await page.evaluate(() => {
      function s4() { return Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1); }
      return s4() + s4() + '-' + s4() + '-' + s4() + '-' + s4() + '-' + s4() + s4() + s4();
    });
    await page.evaluate(([host, port]) => {
      return new Promise((resolve, reject) => {
        // eslint-disable-next-line no-undef
        mockServerClient(host, parseInt(port), null, true).clear().then(resolve, reject);
      });
    }, [MOCKSERVER_HOST, MOCKSERVER_PORT]);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var clientOverTls = mockServerClient(host, parseInt(port), null, true).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          clientOverTls.mockWithCallback({
            'method': 'POST',
            'path': '/somePath',
            'queryStringParameters': [{ 'name': 'test', 'values': ['true'] }],
            'body': { 'type': "STRING", 'string': 'someBody' }
          }, function (request) {
            if (request.method === 'POST' && request.path === '/somePath') {
              return { 'statusCode': 200, 'body': JSON.stringify({name: 'value'}) };
            } else {
              return { 'statusCode': 406 };
            }
          }).then(function () {
            var results = [];
            var xhr1 = new XMLHttpRequest();
            xhr1.onload = function () {
              results.push({ step: 'non-match', status: this.status });
              var xhr2 = new XMLHttpRequest();
              xhr2.onload = function () {
                results.push({ step: 'match', status: this.status, body: this.responseText });
                var xhr3 = new XMLHttpRequest();
                xhr3.onload = function () {
                  results.push({ step: 'exhausted', status: this.status });
                  resolve(results);
                };
                xhr3.open("POST", "https://" + host + ":" + port + "/somePath?test=true");
                xhr3.setRequestHeader("Vary", uuid);
                xhr3.send("someBody");
              };
              xhr2.open("POST", "https://" + host + ":" + port + "/somePath?test=true");
              xhr2.setRequestHeader("Vary", uuid);
              xhr2.send("someBody");
            };
            xhr1.open("GET", "https://" + host + ":" + port + "/otherPath");
            xhr1.setRequestHeader("Vary", uuid);
            xhr1.send();
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result[0].status).toBe(404);
      expect(result[1].status).toBe(200);
      expect(result[1].body).toBe('{"name":"value"}');
      expect(result[2].status).toBe(404);
    } finally {
      await context.close();
    }
  });

  test('should create multiple parallel expectation with method callback', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockWithCallback({
            'method': 'GET', 'path': '/one'
          }, function (request) {
            if (request.method === 'GET' && request.path === '/one') {
              return { 'statusCode': 201, 'body': 'one' };
            } else {
              return { 'statusCode': 406 };
            }
          }, { remainingTimes: 2, unlimited: false }).then(function () {
            client.mockWithCallback({
              'method': 'GET', 'path': '/two'
            }, function (request) {
              if (request.method === 'GET' && request.path === '/two') {
                return { 'statusCode': 202, 'body': 'two' };
              } else {
                return { 'statusCode': 406 };
              }
            }).then(function () {
              var results = [];
              var xhr1 = new XMLHttpRequest();
              xhr1.onload = function () {
                results.push({ step: 'one-1', status: this.status, body: this.responseText });
                var xhr2 = new XMLHttpRequest();
                xhr2.onload = function () {
                  results.push({ step: 'two', status: this.status, body: this.responseText });
                  var xhr3 = new XMLHttpRequest();
                  xhr3.onload = function () {
                    results.push({ step: 'one-2', status: this.status, body: this.responseText });
                    resolve(results);
                  };
                  xhr3.open("GET", "http://" + host + ":" + port + "/one");
                  xhr3.setRequestHeader("Vary", uuid);
                  xhr3.send();
                };
                xhr2.open("GET", "http://" + host + ":" + port + "/two");
                xhr2.setRequestHeader("Vary", uuid);
                xhr2.send("someBody");
              };
              xhr1.open("GET", "http://" + host + ":" + port + "/one");
              xhr1.setRequestHeader("Vary", uuid);
              xhr1.send();
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result[0].status).toBe(201);
      expect(result[0].body).toBe('one');
      expect(result[1].status).toBe(202);
      expect(result[1].body).toBe('two');
      expect(result[2].status).toBe(201);
      expect(result[2].body).toBe('one');
    } finally {
      await context.close();
    }
  });

  test('should create expectation with method callback with numeric times', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockWithCallback({
            'method': 'GET', 'path': '/one'
          }, function (request) {
            if (request.method === 'GET' && request.path === '/one') {
              return { 'statusCode': 201, 'body': 'one' };
            } else {
              return { 'statusCode': 406 };
            }
          }, 2).then(function () {
            var results = [];
            var xhr1 = new XMLHttpRequest();
            xhr1.onload = function () {
              results.push({ step: 'first', status: this.status, body: this.responseText });
              var xhr2 = new XMLHttpRequest();
              xhr2.onload = function () {
                results.push({ step: 'second', status: this.status, body: this.responseText });
                var xhr3 = new XMLHttpRequest();
                xhr3.onload = function () {
                  results.push({ step: 'exhausted', status: this.status, body: this.responseText });
                  resolve(results);
                };
                xhr3.open("GET", "http://" + host + ":" + port + "/one");
                xhr3.setRequestHeader("Vary", uuid);
                xhr3.send();
              };
              xhr2.open("GET", "http://" + host + ":" + port + "/one");
              xhr2.setRequestHeader("Vary", uuid);
              xhr2.send();
            };
            xhr1.open("GET", "http://" + host + ":" + port + "/one");
            xhr1.setRequestHeader("Vary", uuid);
            xhr1.send();
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result[0].status).toBe(201);
      expect(result[0].body).toBe('one');
      expect(result[1].status).toBe(201);
      expect(result[1].body).toBe('one');
      expect(result[2].status).toBe(404);
    } finally {
      await context.close();
    }
  });

  test('should update default headers for simple response expectation', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.setDefaultHeaders([
            {"name": "Content-Type", "values": ["application/json; charset=utf-8"]},
            {"name": "Location", "values": ["test-value"]}
          ]);
          client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
            var xhr = new XMLHttpRequest();
            xhr.onload = function () {
              resolve({
                status: this.status,
                body: this.responseText,
                location: this.getResponseHeader("Location")
              });
            };
            xhr.open("POST", "http://" + host + ":" + port + "/somePath?test=true");
            xhr.setRequestHeader("Vary", uuid);
            xhr.send("someBody");
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.status).toBe(203);
      expect(result.body).toBe('{"name":"value"}');
      expect(result.location).toBe("test-value");
    } finally {
      await context.close();
    }
  });

  test('should verify exact number of requests have been sent', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
            var xhr = new XMLHttpRequest();
            xhr.onload = function () {
              client.verify({
                'method': 'POST', 'path': '/somePath', 'body': 'someBody'
              }, 1, 1).then(function () {
                resolve({ verified: true });
              }, function (err) {
                resolve({ verified: false, error: err });
              });
            };
            xhr.open("POST", "http://" + host + ":" + port + "/somePath");
            xhr.setRequestHeader("Vary", uuid);
            xhr.send("someBody");
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.verified).toBe(true);
    } finally {
      await context.close();
    }
  });

  test('should verify at least a number of requests have been sent', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
            client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
              var xhr1 = new XMLHttpRequest();
              xhr1.onload = function () {
                var xhr2 = new XMLHttpRequest();
                xhr2.onload = function () {
                  client.verify({
                    'method': 'POST', 'path': '/somePath', 'body': 'someBody'
                  }, 1).then(function () {
                    resolve({ verified: true });
                  }, function (err) {
                    resolve({ verified: false, error: err });
                  });
                };
                xhr2.open("POST", "http://" + host + ":" + port + "/somePath");
                xhr2.setRequestHeader("Vary", uuid);
                xhr2.send("someBody");
              };
              xhr1.open("POST", "http://" + host + ":" + port + "/somePath");
              xhr1.setRequestHeader("Vary", uuid);
              xhr1.send("someBody");
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.verified).toBe(true);
    } finally {
      await context.close();
    }
  });

  test('should fail when no requests have been sent', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
            var xhr = new XMLHttpRequest();
            xhr.onload = function () {
              client.verify({ 'path': '/someOtherPath' }, 1).then(function () {
                resolve({ verified: true });
              }, function (error) {
                resolve({ verified: false, error: error });
              });
            };
            xhr.open("POST", "http://" + host + ":" + port + "/somePath");
            xhr.setRequestHeader("Vary", uuid);
            xhr.send("someBody");
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.verified).toBe(false);
      expect(result.error).toBe("Request not found at least once");
    } finally {
      await context.close();
    }
  });

  test('should fail when not enough exact requests have been sent', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
            var xhr = new XMLHttpRequest();
            xhr.onload = function () {
              client.verify({
                'method': 'POST', 'path': '/somePath', 'body': 'someBody'
              }, 2, 3).then(function () {
                resolve({ verified: true });
              }, function (error) {
                resolve({ verified: false, error: error });
              });
            };
            xhr.open("POST", "http://" + host + ":" + port + "/somePath");
            xhr.setRequestHeader("Vary", uuid);
            xhr.send("someBody");
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.verified).toBe(false);
      expect(result.error).toBe("Request not found between 2 and 3 times");
    } finally {
      await context.close();
    }
  });

  test('should fail when not enough at least requests have been sent', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
            var xhr = new XMLHttpRequest();
            xhr.onload = function () {
              client.verify({
                'method': 'POST', 'path': '/somePath', 'body': 'someBody'
              }, 2).then(function () {
                resolve({ verified: true });
              }, function (error) {
                resolve({ verified: false, error: error });
              });
            };
            xhr.open("POST", "http://" + host + ":" + port + "/somePath");
            xhr.setRequestHeader("Vary", uuid);
            xhr.send("someBody");
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.verified).toBe(false);
      expect(result.error).toBe("Request not found at least 2 times");
    } finally {
      await context.close();
    }
  });

  test('should pass when correct sequence of requests have been sent', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
            client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {
              var xhr1 = new XMLHttpRequest();
              xhr1.onload = function () {
                var xhr2 = new XMLHttpRequest();
                xhr2.onload = function () {
                  var xhr3 = new XMLHttpRequest();
                  xhr3.onload = function () {
                    client.verifySequence(
                      { 'method': 'POST', 'path': '/somePathOne', 'body': 'someBody' },
                      { 'method': 'GET', 'path': '/notFound' },
                      { 'method': 'GET', 'path': '/somePathTwo' }
                    ).then(function () {
                      resolve({ verified: true });
                    }, function (error) {
                      resolve({ verified: false, error: error });
                    });
                  };
                  xhr3.open("GET", "http://" + host + ":" + port + "/somePathTwo");
                  xhr3.setRequestHeader("Vary", uuid);
                  xhr3.send();
                };
                xhr2.open("GET", "http://" + host + ":" + port + "/notFound");
                xhr2.setRequestHeader("Vary", uuid);
                xhr2.send();
              };
              xhr1.open("POST", "http://" + host + ":" + port + "/somePathOne");
              xhr1.setRequestHeader("Vary", uuid);
              xhr1.send("someBody");
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.verified).toBe(true);
    } finally {
      await context.close();
    }
  });

  test('should fail when incorrect sequence (wrong order) of requests have been sent', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
            client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {
              var xhr1 = new XMLHttpRequest();
              xhr1.onload = function () {
                var xhr2 = new XMLHttpRequest();
                xhr2.onload = function () {
                  var xhr3 = new XMLHttpRequest();
                  xhr3.onload = function () {
                    client.verifySequence(
                      { 'method': 'POST', 'path': '/somePathOne', 'body': 'someBody' },
                      { 'method': 'GET', 'path': '/somePathTwo' },
                      { 'method': 'GET', 'path': '/notFound' }
                    ).then(function () {
                      resolve({ verified: true });
                    }, function (error) {
                      resolve({ verified: false, error: error });
                    });
                  };
                  xhr3.open("GET", "http://" + host + ":" + port + "/somePathTwo");
                  xhr3.setRequestHeader("Vary", uuid);
                  xhr3.send();
                };
                xhr2.open("GET", "http://" + host + ":" + port + "/notFound");
                xhr2.setRequestHeader("Vary", uuid);
                xhr2.send();
              };
              xhr1.open("POST", "http://" + host + ":" + port + "/somePathOne");
              xhr1.setRequestHeader("Vary", uuid);
              xhr1.send("someBody");
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.verified).toBe(false);
      expect(result.error).toBe("Request sequence not found");
    } finally {
      await context.close();
    }
  });

  test('should fail when incorrect sequence (first request incorrect body) of requests have been sent', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
            client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {
              var xhr1 = new XMLHttpRequest();
              xhr1.onload = function () {
                var xhr2 = new XMLHttpRequest();
                xhr2.onload = function () {
                  var xhr3 = new XMLHttpRequest();
                  xhr3.onload = function () {
                    client.verifySequence(
                      { 'method': 'POST', 'path': '/somePathOne', 'body': 'some_incorrect_body' },
                      { 'method': 'GET', 'path': '/notFound' },
                      { 'method': 'GET', 'path': '/somePathTwo' }
                    ).then(function () {
                      resolve({ verified: true });
                    }, function (error) {
                      resolve({ verified: false, error: error });
                    });
                  };
                  xhr3.open("GET", "http://" + host + ":" + port + "/somePathTwo");
                  xhr3.setRequestHeader("Vary", uuid);
                  xhr3.send();
                };
                xhr2.open("GET", "http://" + host + ":" + port + "/notFound");
                xhr2.setRequestHeader("Vary", uuid);
                xhr2.send();
              };
              xhr1.open("POST", "http://" + host + ":" + port + "/somePathOne");
              xhr1.setRequestHeader("Vary", uuid);
              xhr1.send("someBody");
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.verified).toBe(false);
      expect(result.error).toBe("Request sequence not found");
    } finally {
      await context.close();
    }
  });

  test('should clear expectations and logs by path', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
            client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
              client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200).then(function () {
                var xhr = new XMLHttpRequest();
                xhr.onload = function () {
                  var matchStatus = this.status;
                  client.clear('/somePathOne').then(function () {
                    var xhr2 = new XMLHttpRequest();
                    xhr2.onload = function () {
                      var clearedStatus = this.status;
                      client.clear('/somePathOne').then(function () {
                        client.retrieveRecordedRequests({
                          "httpRequest": { "path": "/somePathOne" }
                        }).then(function (requests) {
                          var xhr3 = new XMLHttpRequest();
                          xhr3.onload = function () {
                            client.retrieveRecordedRequests({
                              "httpRequest": { "path": "/somePathTwo" }
                            }).then(function (requests2) {
                              resolve({
                                matchStatus: matchStatus,
                                clearedStatus: clearedStatus,
                                pathOneRequestsAfterClear: requests.length,
                                pathTwoStatus: xhr3.status,
                                pathTwoBody: xhr3.responseText,
                                pathTwoRequests: requests2.length,
                                pathTwoRequestPath: requests2[0] ? requests2[0].path : null
                              });
                            }, reject);
                          };
                          xhr3.open("GET", "http://" + host + ":" + port + "/somePathTwo");
                          xhr3.setRequestHeader("Vary", uuid);
                          xhr3.send();
                        }, reject);
                      }, reject);
                    };
                    xhr2.open("GET", "http://" + host + ":" + port + "/somePathOne");
                    xhr2.setRequestHeader("Vary", uuid);
                    xhr2.send();
                  }, reject);
                };
                xhr.open("GET", "http://" + host + ":" + port + "/somePathOne");
                xhr.setRequestHeader("Vary", uuid);
                xhr.send();
              }, reject);
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.matchStatus).toBe(200);
      expect(result.clearedStatus).toBe(404);
      expect(result.pathOneRequestsAfterClear).toBe(0);
      expect(result.pathTwoStatus).toBe(200);
      expect(result.pathTwoBody).toBe('{"name":"value"}');
      expect(result.pathTwoRequests).toBe(1);
      expect(result.pathTwoRequestPath).toBe('/somePathTwo');
    } finally {
      await context.close();
    }
  });

  test('should clear expectations by request matcher', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
            client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
              client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200).then(function () {
                var xhr = new XMLHttpRequest();
                xhr.onload = function () {
                  var matchStatus = this.status;
                  client.clear({ "path": "/somePathOne" }).then(function () {
                    var xhr2 = new XMLHttpRequest();
                    xhr2.onload = function () {
                      var clearedStatus = this.status;
                      var xhr3 = new XMLHttpRequest();
                      xhr3.onload = function () {
                        resolve({
                          matchStatus: matchStatus,
                          clearedStatus: clearedStatus,
                          notClearedStatus: this.status,
                          notClearedBody: this.responseText
                        });
                      };
                      xhr3.open("GET", "http://" + host + ":" + port + "/somePathTwo");
                      xhr3.setRequestHeader("Vary", uuid);
                      xhr3.send();
                    };
                    xhr2.open("GET", "http://" + host + ":" + port + "/somePathOne");
                    xhr2.setRequestHeader("Vary", uuid);
                    xhr2.send();
                  }, reject);
                };
                xhr.open("GET", "http://" + host + ":" + port + "/somePathOne");
                xhr.setRequestHeader("Vary", uuid);
                xhr.send();
              }, reject);
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.matchStatus).toBe(200);
      expect(result.clearedStatus).toBe(404);
      expect(result.notClearedStatus).toBe(200);
      expect(result.notClearedBody).toBe('{"name":"value"}');
    } finally {
      await context.close();
    }
  });

  test('should clear expectations by expectation matcher', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
            client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
              client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200).then(function () {
                var xhr = new XMLHttpRequest();
                xhr.onload = function () {
                  var matchStatus = this.status;
                  client.clear({ "httpRequest": { "path": "/somePathOne" } }).then(function () {
                    var xhr2 = new XMLHttpRequest();
                    xhr2.onload = function () {
                      var clearedStatus = this.status;
                      var xhr3 = new XMLHttpRequest();
                      xhr3.onload = function () {
                        resolve({
                          matchStatus: matchStatus,
                          clearedStatus: clearedStatus,
                          notClearedStatus: this.status,
                          notClearedBody: this.responseText
                        });
                      };
                      xhr3.open("GET", "http://" + host + ":" + port + "/somePathTwo");
                      xhr3.setRequestHeader("Vary", uuid);
                      xhr3.send();
                    };
                    xhr2.open("GET", "http://" + host + ":" + port + "/somePathOne");
                    xhr2.setRequestHeader("Vary", uuid);
                    xhr2.send();
                  }, reject);
                };
                xhr.open("GET", "http://" + host + ":" + port + "/somePathOne");
                xhr.setRequestHeader("Vary", uuid);
                xhr.send();
              }, reject);
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.matchStatus).toBe(200);
      expect(result.clearedStatus).toBe(404);
      expect(result.notClearedStatus).toBe(200);
      expect(result.notClearedBody).toBe('{"name":"value"}');
    } finally {
      await context.close();
    }
  });

  test('should clear only expectations by path', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
            client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
              client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200).then(function () {
                // matching request
                var xhr = new XMLHttpRequest();
                xhr.onload = function () {
                  var matchStatus = this.status;
                  // clear only expectations (not logs) by path
                  client.clear("/somePathOne", "EXPECTATIONS").then(function () {
                    // expectation cleared, should 404
                    var xhr2 = new XMLHttpRequest();
                    xhr2.onload = function () {
                      var clearedStatus = this.status;
                      // clear again (no-op for expectations)
                      client.clear("/somePathOne", "EXPECTATIONS").then(function () {
                        // logs should still exist (only expectations were cleared)
                        client.retrieveRecordedRequests({
                          "httpRequest": { "path": "/somePathOne" }
                        }).then(function (requests) {
                          resolve({
                            matchStatus: matchStatus,
                            clearedStatus: clearedStatus,
                            requestCount: requests.length,
                            firstPath: requests[0] ? requests[0].path : null,
                            secondPath: requests[1] ? requests[1].path : null
                          });
                        }, reject);
                      }, reject);
                    };
                    xhr2.open("GET", "http://" + host + ":" + port + "/somePathOne");
                    xhr2.setRequestHeader("Vary", uuid);
                    xhr2.send();
                  }, reject);
                };
                xhr.open("GET", "http://" + host + ":" + port + "/somePathOne");
                xhr.setRequestHeader("Vary", uuid);
                xhr.send();
              }, reject);
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.matchStatus).toBe(200);
      expect(result.clearedStatus).toBe(404);
      // logs should still be present (2 requests to /somePathOne were recorded)
      expect(result.requestCount).toBe(2);
      expect(result.firstPath).toBe('/somePathOne');
      expect(result.secondPath).toBe('/somePathOne');
    } finally {
      await context.close();
    }
  });

  test('should clear only logs by path', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
            client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
              client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200).then(function () {
                // matching request
                var xhr = new XMLHttpRequest();
                xhr.onload = function () {
                  var matchStatus = this.status;
                  var matchBody = this.responseText;
                  // clear only logs (not expectations) by path
                  client.clear("/somePathOne", "LOG").then(function () {
                    // expectations should still work (only logs cleared)
                    var xhr2 = new XMLHttpRequest();
                    xhr2.onload = function () {
                      var secondMatchStatus = this.status;
                      var secondMatchBody = this.responseText;
                      // clear logs again
                      client.clear("/somePathOne", "LOG").then(function () {
                        // recorded requests should be empty (logs were cleared)
                        client.retrieveRecordedRequests({
                          "httpRequest": { "path": "/somePathOne" }
                        }).then(function (requests) {
                          resolve({
                            matchStatus: matchStatus,
                            matchBody: matchBody,
                            secondMatchStatus: secondMatchStatus,
                            secondMatchBody: secondMatchBody,
                            requestCount: requests.length
                          });
                        }, reject);
                      }, reject);
                    };
                    xhr2.open("GET", "http://" + host + ":" + port + "/somePathOne");
                    xhr2.setRequestHeader("Vary", uuid);
                    xhr2.send();
                  }, reject);
                };
                xhr.open("GET", "http://" + host + ":" + port + "/somePathOne");
                xhr.setRequestHeader("Vary", uuid);
                xhr.send();
              }, reject);
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.matchStatus).toBe(200);
      expect(result.matchBody).toBe('{"name":"value"}');
      // expectations still work (only logs were cleared)
      expect(result.secondMatchStatus).toBe(200);
      expect(result.secondMatchBody).toBe('{"name":"value"}');
      // logs should be empty after clearing
      expect(result.requestCount).toBe(0);
    } finally {
      await context.close();
    }
  });

  test('should reset expectations', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
            client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
              client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200).then(function () {
                var xhr = new XMLHttpRequest();
                xhr.onload = function () {
                  var matchStatus = this.status;
                  client.clear().then(function () {
                    var xhr2 = new XMLHttpRequest();
                    xhr2.onload = function () {
                      var resetStatus1 = this.status;
                      var xhr3 = new XMLHttpRequest();
                      xhr3.onload = function () {
                        resolve({
                          matchStatus: matchStatus,
                          resetStatus1: resetStatus1,
                          resetStatus2: this.status
                        });
                      };
                      xhr3.open("GET", "http://" + host + ":" + port + "/somePathTwo");
                      xhr3.setRequestHeader("Vary", uuid);
                      xhr3.send();
                    };
                    xhr2.open("GET", "http://" + host + ":" + port + "/somePathOne");
                    xhr2.setRequestHeader("Vary", uuid);
                    xhr2.send();
                  }, reject);
                };
                xhr.open("GET", "http://" + host + ":" + port + "/somePathOne");
                xhr.setRequestHeader("Vary", uuid);
                xhr.send();
              }, reject);
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.matchStatus).toBe(200);
      expect(result.resetStatus1).toBe(404);
      expect(result.resetStatus2).toBe(404);
    } finally {
      await context.close();
    }
  });

  test('should retrieve some recorded requests using object matcher', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
              client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {
                var xhr1 = new XMLHttpRequest();
                xhr1.onload = function () {
                  var xhr2 = new XMLHttpRequest();
                  xhr2.onload = function () {
                    var xhr3 = new XMLHttpRequest();
                    xhr3.onload = function () {
                      var xhr4 = new XMLHttpRequest();
                      xhr4.onload = function () {
                        client.retrieveRecordedRequests({
                          "httpRequest": { "path": "/somePathOne" }
                        }).then(function (requests) {
                          resolve({
                            count: requests.length,
                            first: { path: requests[0].path, method: requests[0].method },
                            second: { path: requests[1].path, method: requests[1].method }
                          });
                        }, reject);
                      };
                      xhr4.open("GET", "http://" + host + ":" + port + "/somePathTwo");
                      xhr4.setRequestHeader("Vary", uuid);
                      xhr4.send();
                    };
                    xhr3.open("GET", "http://" + host + ":" + port + "/notFound");
                    xhr3.setRequestHeader("Vary", uuid);
                    xhr3.send();
                  };
                  xhr2.open("GET", "http://" + host + ":" + port + "/somePathOne");
                  xhr2.setRequestHeader("Vary", uuid);
                  xhr2.send();
                };
                xhr1.open("POST", "http://" + host + ":" + port + "/somePathOne");
                xhr1.setRequestHeader("Vary", uuid);
                xhr1.send("someBody");
              }, reject);
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.count).toBe(2);
      expect(result.first.path).toBe('/somePathOne');
      expect(result.first.method).toBe('POST');
      expect(result.second.path).toBe('/somePathOne');
      expect(result.second.method).toBe('GET');
    } finally {
      await context.close();
    }
  });

  test('should retrieve some recorded requests using path', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
              client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {
                var xhr1 = new XMLHttpRequest();
                xhr1.onload = function () {
                  var xhr2 = new XMLHttpRequest();
                  xhr2.onload = function () {
                    var xhr3 = new XMLHttpRequest();
                    xhr3.onload = function () {
                      var xhr4 = new XMLHttpRequest();
                      xhr4.onload = function () {
                        client.retrieveRecordedRequests("/somePathOne").then(function (requests) {
                          resolve({
                            count: requests.length,
                            first: { path: requests[0].path, method: requests[0].method },
                            second: { path: requests[1].path, method: requests[1].method }
                          });
                        }, reject);
                      };
                      xhr4.open("GET", "http://" + host + ":" + port + "/somePathTwo");
                      xhr4.setRequestHeader("Vary", uuid);
                      xhr4.send();
                    };
                    xhr3.open("GET", "http://" + host + ":" + port + "/notFound");
                    xhr3.setRequestHeader("Vary", uuid);
                    xhr3.send();
                  };
                  xhr2.open("GET", "http://" + host + ":" + port + "/somePathOne");
                  xhr2.setRequestHeader("Vary", uuid);
                  xhr2.send();
                };
                xhr1.open("POST", "http://" + host + ":" + port + "/somePathOne");
                xhr1.setRequestHeader("Vary", uuid);
                xhr1.send("someBody");
              }, reject);
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.count).toBe(2);
      expect(result.first.path).toBe('/somePathOne');
      expect(result.first.method).toBe('POST');
      expect(result.second.path).toBe('/somePathOne');
      expect(result.second.method).toBe('GET');
    } finally {
      await context.close();
    }
  });

  test('should retrieve all recorded requests using object matcher', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
              client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {
                var xhr1 = new XMLHttpRequest();
                xhr1.onload = function () {
                  var xhr2 = new XMLHttpRequest();
                  xhr2.onload = function () {
                    var xhr3 = new XMLHttpRequest();
                    xhr3.onload = function () {
                      var xhr4 = new XMLHttpRequest();
                      xhr4.onload = function () {
                        client.retrieveRecordedRequests({
                          "httpRequest": { "path": "/.*" }
                        }).then(function (requests) {
                          resolve({
                            count: requests.length,
                            first: { path: requests[0].path, method: requests[0].method },
                            second: { path: requests[1].path, method: requests[1].method },
                            third: { path: requests[2].path, method: requests[2].method },
                            fourth: { path: requests[3].path, method: requests[3].method }
                          });
                        }, reject);
                      };
                      xhr4.open("GET", "http://" + host + ":" + port + "/somePathTwo");
                      xhr4.setRequestHeader("Vary", uuid);
                      xhr4.send();
                    };
                    xhr3.open("GET", "http://" + host + ":" + port + "/notFound");
                    xhr3.setRequestHeader("Vary", uuid);
                    xhr3.send();
                  };
                  xhr2.open("GET", "http://" + host + ":" + port + "/somePathOne");
                  xhr2.setRequestHeader("Vary", uuid);
                  xhr2.send();
                };
                xhr1.open("POST", "http://" + host + ":" + port + "/somePathOne");
                xhr1.setRequestHeader("Vary", uuid);
                xhr1.send("someBody");
              }, reject);
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.count).toBe(4);
      expect(result.first.path).toBe('/somePathOne');
      expect(result.first.method).toBe('POST');
      expect(result.second.path).toBe('/somePathOne');
      expect(result.second.method).toBe('GET');
      expect(result.third.path).toBe('/notFound');
      expect(result.third.method).toBe('GET');
      expect(result.fourth.path).toBe('/somePathTwo');
      expect(result.fourth.method).toBe('GET');
    } finally {
      await context.close();
    }
  });

  test('should retrieve all recorded requests using null matcher', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
              client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {
                var xhr1 = new XMLHttpRequest();
                xhr1.onload = function () {
                  var xhr2 = new XMLHttpRequest();
                  xhr2.onload = function () {
                    var xhr3 = new XMLHttpRequest();
                    xhr3.onload = function () {
                      var xhr4 = new XMLHttpRequest();
                      xhr4.onload = function () {
                        client.retrieveRecordedRequests().then(function (requests) {
                          resolve({
                            count: requests.length,
                            first: { path: requests[0].path, method: requests[0].method },
                            second: { path: requests[1].path, method: requests[1].method },
                            third: { path: requests[2].path, method: requests[2].method },
                            fourth: { path: requests[3].path, method: requests[3].method }
                          });
                        }, reject);
                      };
                      xhr4.open("GET", "http://" + host + ":" + port + "/somePathTwo");
                      xhr4.setRequestHeader("Vary", uuid);
                      xhr4.send();
                    };
                    xhr3.open("GET", "http://" + host + ":" + port + "/notFound");
                    xhr3.setRequestHeader("Vary", uuid);
                    xhr3.send();
                  };
                  xhr2.open("GET", "http://" + host + ":" + port + "/somePathOne");
                  xhr2.setRequestHeader("Vary", uuid);
                  xhr2.send();
                };
                xhr1.open("POST", "http://" + host + ":" + port + "/somePathOne");
                xhr1.setRequestHeader("Vary", uuid);
                xhr1.send("someBody");
              }, reject);
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.count).toBe(4);
      expect(result.first.path).toBe('/somePathOne');
      expect(result.first.method).toBe('POST');
      expect(result.second.path).toBe('/somePathOne');
      expect(result.second.method).toBe('GET');
      expect(result.third.path).toBe('/notFound');
      expect(result.third.method).toBe('GET');
      expect(result.fourth.path).toBe('/somePathTwo');
      expect(result.fourth.method).toBe('GET');
    } finally {
      await context.close();
    }
  });

  test('should retrieve some active expectations using object matcher', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
              client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 303).then(function () {
                client.retrieveActiveExpectations({
                  "httpRequest": { "path": "/somePathOne" }
                }).then(function (expectations) {
                  resolve({
                    count: expectations.length,
                    first: {
                      path: expectations[0].httpRequest.path,
                      body: expectations[0].httpResponse.body,
                      statusCode: expectations[0].httpResponse.statusCode
                    },
                    second: {
                      path: expectations[1].httpRequest.path,
                      body: expectations[1].httpResponse.body,
                      statusCode: expectations[1].httpResponse.statusCode
                    }
                  });
                }, reject);
              }, reject);
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.count).toBe(2);
      expect(result.first.path).toBe('/somePathOne');
      expect(result.first.body).toBe('{"name":"one"}');
      expect(result.first.statusCode).toBe(201);
      expect(result.second.path).toBe('/somePathOne');
      expect(result.second.body).toBe('{"name":"one"}');
      expect(result.second.statusCode).toBe(201);
    } finally {
      await context.close();
    }
  });

  test('should retrieve some active expectations using path', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
              client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {
                client.retrieveActiveExpectations("/somePathOne").then(function (expectations) {
                  resolve({
                    count: expectations.length,
                    first: {
                      path: expectations[0].httpRequest.path,
                      body: expectations[0].httpResponse.body,
                      statusCode: expectations[0].httpResponse.statusCode
                    },
                    second: {
                      path: expectations[1].httpRequest.path,
                      body: expectations[1].httpResponse.body,
                      statusCode: expectations[1].httpResponse.statusCode
                    }
                  });
                }, reject);
              }, reject);
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.count).toBe(2);
      expect(result.first.path).toBe('/somePathOne');
      expect(result.first.body).toBe('{"name":"one"}');
      expect(result.first.statusCode).toBe(201);
      expect(result.second.path).toBe('/somePathOne');
      expect(result.second.body).toBe('{"name":"one"}');
      expect(result.second.statusCode).toBe(201);
    } finally {
      await context.close();
    }
  });

  test('should retrieve all active expectations using object matcher', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
              client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {
                client.retrieveActiveExpectations({
                  "httpRequest": { "path": "/somePath.*" }
                }).then(function (expectations) {
                  resolve({
                    count: expectations.length,
                    first: {
                      path: expectations[0].httpRequest.path,
                      body: expectations[0].httpResponse.body,
                      statusCode: expectations[0].httpResponse.statusCode
                    },
                    second: {
                      path: expectations[1].httpRequest.path,
                      body: expectations[1].httpResponse.body,
                      statusCode: expectations[1].httpResponse.statusCode
                    },
                    third: {
                      path: expectations[2].httpRequest.path,
                      body: expectations[2].httpResponse.body,
                      statusCode: expectations[2].httpResponse.statusCode
                    }
                  });
                }, reject);
              }, reject);
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.count).toBe(3);
      expect(result.first.path).toBe('/somePathOne');
      expect(result.first.body).toBe('{"name":"one"}');
      expect(result.first.statusCode).toBe(201);
      expect(result.second.path).toBe('/somePathOne');
      expect(result.second.body).toBe('{"name":"one"}');
      expect(result.second.statusCode).toBe(201);
      expect(result.third.path).toBe('/somePathTwo');
      expect(result.third.body).toBe('{"name":"two"}');
      expect(result.third.statusCode).toBe(202);
    } finally {
      await context.close();
    }
  });

  test('should retrieve all active expectations using null matcher', async ({ browser }) => {
    const { page, context, uuid } = await setupPage(browser);
    try {
      const result = await page.evaluate(([host, port, uuid]) => {
        return new Promise((resolve, reject) => {
          var client = mockServerClient(host, parseInt(port)).setDefaultHeaders(undefined, [
            {"name": "Vary", "values": [uuid]}
          ]);
          client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
              client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {
                client.retrieveActiveExpectations().then(function (expectations) {
                  resolve({
                    count: expectations.length,
                    third: {
                      path: expectations[2].httpRequest.path,
                      body: expectations[2].httpResponse.body,
                      statusCode: expectations[2].httpResponse.statusCode
                    }
                  });
                }, reject);
              }, reject);
            }, reject);
          }, reject);
        });
      }, [MOCKSERVER_HOST, MOCKSERVER_PORT, uuid]);

      expect(result.count).toBe(3);
      expect(result.third.path).toBe('/somePathTwo');
      expect(result.third.body).toBe('{"name":"two"}');
      expect(result.third.statusCode).toBe(202);
    } finally {
      await context.close();
    }
  });
});
