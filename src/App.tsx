import "@/App.css";
import { loadConfig } from "@/helpers/config";

function App() {
  return (
    <>
      <h1>App</h1>

      <button onClick={async () => console.log(await loadConfig())}>Load Config</button>
    </>
  );
}

export default App;
