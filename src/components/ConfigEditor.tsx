import useConfigStore from "@/hooks/config";
import useRouter from "@/hooks/router";
import { useState } from "react";

const ConfigEditor = () => {
  const routerStore = useRouter();
  const configStore = useConfigStore();
  const [editedConfig, setEditedConfig] = useState(JSON.stringify(configStore.config, null, 2));

  return (
    <div className="editor" style={{ height: "100%", display: "flex", flexDirection: "column" }}>
      <nav style={{ padding: "1rem", display: "flex", justifyContent: "space-between" }}>
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
      <textarea
        style={{ flex: 1 }}
        autoCapitalize="off"
        autoComplete="off"
        autoCorrect="off"
        spellCheck="false"
        value={editedConfig}
        onChange={e => setEditedConfig(e.target.value)}
      />
    </div>
  );
};

export default ConfigEditor;
