// Karma configuration file, see link for more information
// https://karma-runner.github.io/1.0/config/configuration-file.html

// --- 1. ADD THIS LINE AT THE TOP ---
// This tells Karma to use the Chrome browser downloaded by Puppeteer
process.env.CHROME_BIN = require('puppeteer').executablePath();

process.on('uncaughtException', (err) => {
  if (err.code === 'ERR_SERVER_NOT_RUNNING') {
    console.error(
        'Caught ERR_SERVER_NOT_RUNNING from karma.',
        err);
        console.error(err);
      } else {
        console.error('Caught unhandled exception:', err);
      }
  process.exit(1);
});

/**
 * Karma configuration.
 * @param {!Object} config
 */
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    client: {
      jasmine: {
        // you can add configuration options for Jasmine here
        // the possible options are listed at https://jasmine.github.io/api/edge/Configuration.html
        // for example, you can disable the random execution with `random: false`
        // or set a specific seed with `seed: 4321`
      },
    },
    jasmineHtmlReporter: {
      suppressAll: true // removes the duplicated traces
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/angular'),
      subdir: '.',
      reporters: [
        { type: 'html' },
        { type: 'text-summary' }
      ]
    },
    reporters: ['progress', 'kjhtml'],
    browsers: ['Chrome'], // This is used for local development

    // --- 2. ADD THIS NEW BLOCK ---
    // This creates a custom launcher for your CI environment
    customLaunchers: {
      ChromeHeadlessCI: {
        base: 'ChromeHeadless',
        flags: [
          '--no-sandbox',
          '--disable-gpu'
        ]
      }
    },
    // ----------------------------

    restartOnFileChange: true
  });
};
