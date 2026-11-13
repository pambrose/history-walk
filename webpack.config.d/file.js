/* global config -- the webpack configuration Kotlin/JS defines in the file it pastes this one into */
config.module.rules.push(
    {
        test: /\.(jpe?g|png|gif|svg)$/i,
        type: 'asset/resource'
    }
);
