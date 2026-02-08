import useConfigStore from "@/hooks/config";
import useRouter, { RouterScreen } from "@/hooks/router";
import { useState } from "react";

const ConfigEditor = () => {
  const routerStore = useRouter();
  const configStore = useConfigStore();
  const [editedConfig, setEditedConfig] = useState(JSON.stringify(configStore.config, null, 2));

  return (
    <div className="editor" style={{ height: "100%", display: "flex", flexDirection: "column" }}>
      <nav>
        <button onClick={() => routerStore.navigate(RouterScreen.Main)}>Back</button>
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
        draggable={false}
        style={{ flex: 1 }}
        value={editedConfig}
        onChange={e => setEditedConfig(e.target.value)}
      />
    </div>
  );
};

export default ConfigEditor;
