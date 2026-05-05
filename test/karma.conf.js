module.exports = function (config) {
    config.set({
        basePath: '..',
        frameworks: ['jasmine'],
        files: [],
        exclude: [],
        preprocessors: {},
        reporters: ["spec"],
        specReporter: {
            maxLogLines: 10,
            suppressErrorSummary: false,
            suppressFailed: false,
            suppressPassed: false,
            suppressSkipped: false,
            showSpecTiming: true
        },
        port: 9876,
        colors: true,
        logLevel: config.LOG_INFO,
        autoWatch: true,
        browsers: ['Chrome'],
        singleRun: true,
        concurrency: Infinity,
        customLaunchers: {
            Chrome_with_proxy: {
                base: 'Chrome',
                flags: ['--proxy-server=http://127.0.0.1:1090']
            },
            PhantomJS_with_proxy: {
                base: 'PhantomJS',
                flags: ['--proxy-type=http', '--proxy=127.0.0.1:1090']
            }
        },
        client: {
            captureConsole: true,
            mode: config.mode
        }
    });
};

// PhantomJS flags: ['--web-security=no', '--proxy-type=none', '--remote-debugger-port=9000', '--remote-debugger-autorun=yes']