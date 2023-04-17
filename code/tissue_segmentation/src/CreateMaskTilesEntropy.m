function CreateMaskTilesEntropy(ImageTilePath, MaskTilePath, Ext, Params)
%CREATEMASKTILES Summary of this function goes here
%   Detailed explanation goes here
    if nargin < 3
        Ext = 'jpg';
    end
    
    if nargin < 4
        Params = [3.5 5000 225];
    end

    imageTileFiles = dir(fullfile(ImageTilePath, ['Da*.' Ext]));
    
    if ~isfolder(MaskTilePath)
        mkdir(MaskTilePath);
    end

    parfor i=1:length(imageTileFiles)
        [~, fName, ~] = fileparts(imageTileFiles(i).name);
        G = rgb2gray(imread(fullfile(imageTileFiles(i).folder, imageTileFiles(i).name)));
        
        C = bwconncomp(entropyfilt(G) > Params(1));
        
        A = cellfun(@numel, C.PixelIdxList);
        V = cellfun(@(x) median(G(x)), C.PixelIdxList);
        
        goodPixels = cat(1, C.PixelIdxList{(A > Params(2)) & (V < Params(3))});
        
        B = false(size(G));
        B(goodPixels) = true;
        
        if any(B(:))
            imwrite(B, fullfile(MaskTilePath, [fName '.png']));
        end
    end
end
