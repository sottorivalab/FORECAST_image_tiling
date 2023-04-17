import pickle
import sys

def writeParamP(inPath, outPath):

    inFile = open(inPath, "r")
    
    lines = inFile.readlines()
    
    params = {
        "filename": lines[0].rstrip("\n\r"),
        "slide_dimension": (int(lines[1]), int(lines[2])),
        "exp_dir": lines[3].rstrip("\n\r"),
        "cws_read_size": [int(lines[4]), int(lines[5])],
        "rescale": int(lines[6]),
        "cws_objective_value": int(lines[7]),
        "cws_objective_power": int(lines[8])
    }
    
    inFile.close()
    
    outFile = open(outPath, "wb")
    pickle.dump(params, outFile)
    outFile.close()
    
if __name__ == '__main__':
    writeParamP(sys.argv[1], sys.argv[2])
