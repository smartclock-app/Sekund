import useRouter, { RouterScreen } from "@/hooks/useRouter";
import { memo, useState } from "react";

const OptionsModal = ({ show, onClose }: { show: boolean; onClose: () => void }) => {
  const routerStore = useRouter();

  const handleClickOutside = (e: React.MouseEvent<HTMLDivElement> | React.TouchEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  if (!show) return null;
  return (
    <div
      style={{
        position: "absolute",
        left: 0,
        top: 0,
        width: "100%",
        height: "100%",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        backgroundColor: "rgba(0,0,0,0.5)",
        zIndex: 1000,
      }}
      onClick={handleClickOutside}
      onTouchEnd={handleClickOutside}
    >
      <div style={{ backgroundColor: "white", padding: "1rem", borderRadius: "8px", width: "300px" }}>
        <h2>Options</h2>
        <button
          onClick={() => {
            onClose();
            routerStore.navigate(RouterScreen.ConfigEditor);
          }}
        >
          Config Editor
        </button>
        <button
          onClick={() => {
            onClose();
            routerStore.navigate(RouterScreen.VariablesEditor);
          }}
        >
          Variables Editor
        </button>
      </div>
    </div>
  );
};

const useOptionsModal = () => {
  const [showOptions, setShowOptions] = useState(false);

  const OptionsModalWrapper = memo(() => <OptionsModal show={showOptions} onClose={() => setShowOptions(false)} />);

  return { setShowOptions, OptionsModal: OptionsModalWrapper } as const;
};

export default useOptionsModal;
