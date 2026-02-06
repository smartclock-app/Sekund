import "@/App.css";
import { loadConfig } from "./config/loader";

function App() {
  return (
    <>
      <h1>App</h1>

      <button onClick={async () => console.log((await loadConfig()).toJSONSchema())}>Load Config</button>
    </>
  );
}

export default App;
