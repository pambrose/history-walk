if (config.devServer) {
  const target = 'http://localhost:8080';
  config.devServer.proxy = [
    {
      context: ["/auth", "/logout", "/summary", "/reasons", "/slides", "/slide", "/contentreset", "/userreset", "/static"],
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
