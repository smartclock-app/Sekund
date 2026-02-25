import { useRef } from "react";
import ReactPlayer from "react-player";

const Camera = (props: { src: string; aspectRatio: number }) => {
  const playerRef = useRef<HTMLVideoElement>(null);

  return (
    <ReactPlayer
      style={{ aspectRatio: props.aspectRatio }}
      ref={playerRef}
      src={props.src}
      onCanPlay={() => playerRef.current?.play()}
    />
  );
};

export default Camera;
