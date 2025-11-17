const MonacoWebpackPlugin = require('monaco-editor-webpack-plugin');

module.exports = function(config, _env) {
    config.plugins.push(new MonacoWebpackPlugin({
        languages: [ 'javascript' ]
    }));

    return config;
};
