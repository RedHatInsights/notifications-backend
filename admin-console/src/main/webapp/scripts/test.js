#!/usr/bin/env node

const spawn = require('child_process').spawn;

// Run react-scripts test with output filtering
const test = spawn('react-scripts', ['test'].concat(process.argv.slice(2)), {
    stdio: ['inherit', 'pipe', 'pipe'],
    shell: true,
    env: {
        ...process.env
    }
});

// Filter and forward stdout
test.stdout.on('data', (data) => {
    const lines = data.toString().split('\n');
    lines.forEach(line => {
        // Filter out DEP0169 deprecation warning
        if (line.includes('DEP0169') && line.includes('url.parse()')) {
            return;
        }
        if (line) {
            process.stdout.write(line + '\n');
        }
    });
});

// Filter and forward stderr
test.stderr.on('data', (data) => {
    const lines = data.toString().split('\n');
    lines.forEach(line => {
        // Filter out DEP0169 deprecation warning
        if (line.includes('DEP0169') && line.includes('url.parse()')) {
            return;
        }
        if (line) {
            process.stderr.write(line + '\n');
        }
    });
});

test.on('close', (code) => {
    process.exit(code);
});
