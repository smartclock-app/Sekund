import useConfigStore from "@/hooks/useConfigStore";
import useRouter from "@/hooks/useRouter";
import Editor, { useMonaco } from "@monaco-editor/react";
import { useEffect, useState } from "react";

const ConfigEditor = () => {
  const monaco = useMonaco();
  const routerStore = useRouter();
  const configStore = useConfigStore();
  const [editedConfig, setEditedConfig] = useState(JSON.stringify(configStore.config, null, 2));

  useEffect(() => {
    const mySchema = configStore.configSchema?.toJSONSchema();

    if (monaco) {
      // Define the JSON schema
      monaco.json.jsonDefaults.setDiagnosticsOptions({
        validate: true,
        schemas: [
          {
            uri: `inmemory://model/schema-${configStore.version}.json`,
            fileMatch: ["*"],
            schema: mySchema,
          },
        ],
      });
    }
  }, [monaco]);

  return (
    <div className="editor" style={{ height: "100%" }}>
      <nav
        style={{
          padding: "1rem",
          display: "flex",
          justifyContent: "space-between",
          backgroundColor: "#f0f0f0",
          borderBottom: "1px solid #ccc",
        }}
      >
        <button onClick={() => routerStore.goBack()}>Back</button>
        <button
          onClick={async () => {
            await configStore.editConfig(JSON.parse(editedConfig));
            window.location.reload();
          }}
        >
          Save
        </button>
      </nav>

      <Editor
        height="100%"
        defaultLanguage="json"
        value={editedConfig}
        onChange={value => setEditedConfig(value || "{}")}
      />
    </div>
  );
};

export default ConfigEditor;
