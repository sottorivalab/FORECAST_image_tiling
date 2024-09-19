ImageTilesPath = '../../../tiles';
MaskTilesPath = '../../../masks';

TileFormat='jpg';

MaskMethod = 'E';
EntropyParams = [3.5 500 225];

%MaskMethod = 'T';
%ThresholdParams = [0, 210];

CreateMaskTilesBatch(ImageTilesPath, MaskTilesPath, MaskMethod, TileFormat, EntropyParams);
