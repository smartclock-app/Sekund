import "@/App.css";
import LoadConfig from "@/helpers/config";
import QueryClient from "@/widgets/alexa/QueryClient";
import useConfigStore from "./hooks/config";

function App() {
  const configStore = useConfigStore();

  const testCookies = async () => {
    const client = new QueryClient("cookies.json");
    await client.init();

    const success = await client.getDevices("peak");
    console.log(success);
  };

  return (
    <>
      <h1>App</h1>

      <button onClick={testCookies}>Test Cookies</button>

      <button onClick={async () => configStore.setConfig(await LoadConfig())}>Load Config</button>
      <pre>{JSON.stringify(configStore.config, null, 2)}</pre>
    </>
  );
}

export default App;
