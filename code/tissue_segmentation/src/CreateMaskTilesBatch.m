function CreateMaskTilesBatch(TilesFolder, MasksFolder, Method, varargin)
%CREATEMASKTILEBATCH Summary of this function goes here
%   Detailed explanation goes here
    if nargin < 3
        Method = 'E';
        varargin = {};
    end

    files = dir(TilesFolder);
    files = files(~ismember({files.name}, {'.', '..'}));
    files = files([files.isdir]);

    switch Method
        case 'E'
            maskTileFun = @(x, y) CreateMaskTilesEntropy(x, y, varargin{:});
        case 'T'
            maskTileFun = @(x, y) CreateMaskTilesThreshold(x, y, varargin{:});
        otherwise
            error(['Mask tile method "' Method '" is not recognised.']);
    end
    
    for i=1:length(files)
        InFolder = fullfile(files(i).folder, files(i).name);
        OutFolder = fullfile(MasksFolder, files(i).name);
        
        maskTileFun(InFolder, OutFolder);
    end
end

