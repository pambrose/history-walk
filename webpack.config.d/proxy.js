if (config.devServer) {
  config.devServer.proxy = [
    {
      context: ["/login", "/logout"],
      target: 'http://localhost:8080'
    },
    {
      context: ["/contentreset", "/userreset", "/assign-browser-id"],
      target: 'http://localhost:8080'
    },
    {
      context: ["/reasons", "/slides"],
      target: 'http://localhost:8080'
    },
    {
      context: ["slide/*"],
      target: 'http://localhost:8080'
    },
    {
      context: ["/*.html"],
      target: 'http://localhost:8080'
    },
    {
      context: ["/kv/*", "/kvsse/*"],
      target: 'http://localhost:8080'
    },
    {
      context: ["/kvws/*"],
      target: 'http://localhost:8080',
      ws: true
    }
  ];
}


/*
           runTask(Action {
               sourceMaps = false
               devServer = KotlinWebpackConfig.DevServer(
                   open = false,
                   port = 3000,
                   proxy = mutableMapOf(
                       "/login" to "http://localhost:8080",
                       "/logout" to "http://localhost:8080",
                       "/contentreset" to "http://localhost:8080",
                       "/userreset" to "http://localhost:8080",
                       "/summary" to "http://localhost:8080",
                       "/reasons" to "http://localhost:8080",
                       "/slides" to "http://localhost:8080",
                       "/slide/*" to "http://localhost:8080",
                       "/kv/*" to "http://localhost:8080",
                       "/kvws/*" to mapOf("target" to "ws://localhost:8080", "ws" to true)
                   ),

 */
