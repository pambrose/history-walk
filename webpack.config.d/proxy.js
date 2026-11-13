/* global config -- the webpack configuration Kotlin/JS defines in the file it pastes this one into */
if (config.devServer) {
  const target = 'http://localhost:8080';
  config.devServer.proxy = [
    {
      context: ["/auth", "/logout", "/summary", "/reasons", "/admin", "/slides", "/slide", "/threaddump", "/userreset", "/static"],
      target: target
    },
    {
      context: ["/*.html"],
      target: target
    },
    {
      context: ["/rpc", "/rpcsse"],
      target: target
    },
    {
      context: ["/rpcws"],
      target: target,
      ws: true
    }
  ];
}
