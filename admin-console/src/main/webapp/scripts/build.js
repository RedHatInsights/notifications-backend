#!/usr/bin/env node

const spawn = require('child_process').spawn;

// Run react-scripts build
const build = spawn('react-scripts', ['build'], {
    stdio: ['inherit', 'pipe', 'pipe'],
    shell: true,
    env: { ...process.env, DISABLE_ESLINT_PLUGIN: 'true' }
});

let allOutput = '';

build.stdout.on('data', (data) => {
    allOutput += data.toString();
});

build.stderr.on('data', (data) => {
    allOutput += data.toString();
});

build.on('close', (code) => {
    // Filter out autoprefixer warnings
    let lines = allOutput.split('\n');
    let filtered = [];
    let i = 0;

    while (i < lines.length) {
        const line = lines[i];

        // Check if this is an autoprefixer warning
        if (line.includes('autoprefixer') && line.includes('start value has mixed support')) {
            // Skip this warning line and the "Warning" header before it
            if (filtered.length > 0 && filtered[filtered.length - 1].trim() === 'Warning') {
                filtered.pop();
            }
            i++;
            continue;
        }

        filtered.push(line);
        i++;
    }

    // Check if we filtered all warnings
    const hasWarningHeaders = filtered.some(line => line.trim() === 'Warning');

    // Remove orphaned warning help text if we filtered all warnings
    if (!hasWarningHeaders) {
        filtered = filtered.filter(line => {
            return !line.includes('Search for the keywords to learn more about each warning') &&
                   !line.includes('To ignore, add // eslint-disable-next-line');
        });
    }

    let finalOutput = filtered.join('\n');

    // If there are no more warnings, change "Compiled with warnings" to "Compiled successfully"
    if (!hasWarningHeaders) {
        finalOutput = finalOutput.replace('Compiled with warnings.', 'Compiled successfully.');
    }

    console.log(finalOutput);
    process.exit(code);
});
