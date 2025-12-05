// eslint-disable-next-line @typescript-eslint/no-var-requires, no-undef
const MonacoWebpackPlugin = require('monaco-editor-webpack-plugin');

// eslint-disable-next-line no-undef
module.exports = function(config, _env) {
    config.plugins.push(new MonacoWebpackPlugin({
        languages: [ 'javascript' ]
    }));

    return config;
};
