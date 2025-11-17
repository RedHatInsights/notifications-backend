#!/usr/bin/env node

const spawn = require('child_process').spawn;

// Run react-scripts start with output filtering
const start = spawn('react-scripts', ['start'], {
    stdio: ['inherit', 'pipe', 'pipe'],
    shell: true,
    env: {
        ...process.env
    }
});

// Filter and forward stdout
start.stdout.on('data', (data) => {
    const lines = data.toString().split('\n');
    lines.forEach(line => {
        // Filter out deprecation warnings
        if (line.includes('DEP0169') && line.includes('url.parse()')) {
            return;
        }
        if (line.includes('DEP0060') && line.includes('util._extend')) {
            return;
        }
        if (line.includes('DEP_WEBPACK_DEV_SERVER')) {
            return;
        }
        if (line) {
            process.stdout.write(line + '\n');
        }
    });
});

// Filter and forward stderr
start.stderr.on('data', (data) => {
    const lines = data.toString().split('\n');
    lines.forEach(line => {
        // Filter out deprecation warnings
        if (line.includes('DEP0169') && line.includes('url.parse()')) {
            return;
        }
        if (line.includes('DEP0060') && line.includes('util._extend')) {
            return;
        }
        if (line.includes('DEP_WEBPACK_DEV_SERVER')) {
            return;
        }
        if (line) {
            process.stderr.write(line + '\n');
        }
    });
});

start.on('close', (code) => {
    process.exit(code);
});
