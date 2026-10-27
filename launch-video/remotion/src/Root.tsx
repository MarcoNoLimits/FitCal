import React from 'react';
import { Composition } from 'remotion';
import { FitCalLaunch, FPS, TOTAL } from './Video';
import { BRAG_TOTAL, BragLaunch } from './Brag';

export const Root: React.FC = () => (
  <>
    <Composition
      id="FitCal"
      component={FitCalLaunch}
      durationInFrames={TOTAL}
      fps={FPS}
      width={1080}
      height={1920}
    />
    <Composition
      id="Brag"
      component={BragLaunch}
      durationInFrames={BRAG_TOTAL}
      fps={FPS}
      width={1080}
      height={1920}
    />
    {/* The same video at 96 BPM (Pixabay "Vlog Beat"): 1.25× slower, 25 s, music muxed in afterwards. */}
    <Composition
      id="Brag96"
      component={BragLaunch}
      durationInFrames={BRAG_TOTAL * 1.25}
      fps={FPS}
      width={1080}
      height={1920}
      defaultProps={{ stretch: 1.25, music: null }}
    />
  </>
);
