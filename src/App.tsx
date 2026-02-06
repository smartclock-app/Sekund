import "@/App.css";
import LoadConfig from "@/helpers/config";
import { useState } from "react";

function App() {
  const [config, setConfig] = useState<any>(null);

  return (
    <>
      <h1>App</h1>

      <button onClick={async () => setConfig(await LoadConfig())}>Load Config</button>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
}

export default App;
