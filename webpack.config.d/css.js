/* global config -- the webpack configuration Kotlin/JS defines in the file it pastes this one into */
config.module.rules.push({test: /\.css$/, use: ["style-loader", {loader: "css-loader", options: {sourceMap: false}}]});
