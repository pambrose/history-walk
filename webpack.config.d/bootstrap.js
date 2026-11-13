/* global config -- the webpack configuration Kotlin/JS defines in the file it pastes this one into */
config.module.rules.push({test: /\.(woff|woff2)(\?v=\d+\.\d+\.\d+)?$/, type: 'asset'});
config.module.rules.push({test: /\.ttf(\?v=\d+\.\d+\.\d+)?$/, type: 'asset'});
config.module.rules.push({test: /\.eot(\?v=\d+\.\d+\.\d+)?$/, type: 'asset/resource'});
