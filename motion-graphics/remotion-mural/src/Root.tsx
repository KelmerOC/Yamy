import React from "react";
import { Composition } from "remotion";

const MainScene: React.FC = () => {
  return (
    <div
      style={{
        flex: 1,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        backgroundColor: "#0B131E",
        color: "#ECF3FA",
        fontFamily: "sans-serif",
        fontSize: 48,
        fontWeight: "bold",
      }}
    >
      Remotion Mural
    </div>
  );
};

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="Main"
        component={MainScene}
        durationInFrames={180}
        fps={60}
        width={1080}
        height={1920}
      />
    </>
  );
};
