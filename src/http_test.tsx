import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { useEffect, useState } from "react";

interface HttpRequest {
  method: string;
  path: string;
  headers: [string, string][];
  body: string;
}

function App() {
  const [serverRunning, setServerRunning] = useState(false);
  const [port, setPort] = useState(3030);

  useEffect(() => {
    const unlisten = listen<HttpRequest>("http-request", event => {
      console.log("Received HTTP request:", event.payload);
      handleHttpRequest(event.payload);
    });

    return () => {
      unlisten.then(fn => fn());
    };
  }, []);

  const startServer = async () => {
    try {
      const result = await invoke<string>("start_http_server", { port });
      console.log(result);
      setServerRunning(true);
    } catch (error) {
      console.error("Failed to start server:", error);
    }
  };

  const stopServer = async () => {
    try {
      const result = await invoke<string>("stop_http_server");
      console.log(result);
      setServerRunning(false);
    } catch (error) {
      console.error("Failed to stop server:", error);
    }
  };

  const handleHttpRequest = (request: HttpRequest) => {
    console.log(`${request.method} ${request.path}`);
    console.log("Headers:", request.headers);
    console.log("Body:", request.body);
    // Your custom logic here
  };

  return (
    <div>
      <input type="number" value={port} onChange={e => setPort(Number(e.target.value))} disabled={serverRunning} />

      {!serverRunning ? (
        <button onClick={startServer}>Start Server</button>
      ) : (
        <button onClick={stopServer}>Stop Server</button>
      )}

      {serverRunning && <p>Server running on port {port}</p>}
    </div>
  );
}

export default App;
