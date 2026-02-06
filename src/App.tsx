import "@/App.css";
import LoadConfig from "@/helpers/config";

function App() {
  return (
    <>
      <h1>App</h1>

      <button onClick={async () => console.log(await LoadConfig())}>Load Config</button>
    </>
  );
}

export default App;
