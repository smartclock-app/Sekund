import "@/App.css";
import LoadConfig from "@/helpers/config";
import useConfigStore from "./hooks/config";

function App() {
  const configStore = useConfigStore();

  return (
    <>
      <h1>App</h1>

      <button onClick={async () => configStore.setConfig(await LoadConfig())}>Load Config</button>
      <pre>{JSON.stringify(configStore.config, null, 2)}</pre>
    </>
  );
}

export default App;
