function CreateMaskTilesThreshold(ImageTilePath, MaskTilePath, Ext, Threshold)
%CREATEMASKTILES Summary of this function goes here
%   Detailed explanation goes here
    if nargin < 4
        Threshold = [0 250];
    end
    
    if nargin < 3
        Ext = 'jpg';
    end

    imageTileFiles = dir(fullfile(ImageTilePath, ['Da*.' Ext]));
    
    if ~isfolder(MaskTilePath)
        mkdir(MaskTilePath);
    end

    parfor i=1:length(imageTileFiles)
        [~, fName, ~] = fileparts(imageTileFiles(i).name);
        G = rgb2gray(imread(fullfile(imageTileFiles(i).folder, imageTileFiles(i).name)));
        
        B = (G >= Threshold(1)) & (G <= Threshold(2));
        
        if any(B(:))
            imwrite(B, fullfile(MaskTilePath, [fName '.png']));
        end
    end
end
