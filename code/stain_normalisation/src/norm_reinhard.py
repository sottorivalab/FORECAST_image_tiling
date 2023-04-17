import cv2
import numpy as np

def norm_reinhard(source_image, target_image, background_params=None):

    source_lab = cv2.cvtColor(source_image, cv2.COLOR_RGB2Lab)

    ms = np.mean(source_lab, axis=(0, 1))
    stds = np.std(source_lab, axis=(0, 1))
    
    if np.any(stds == 0):
        return source_image
    
    target_lab = cv2.cvtColor(target_image, cv2.COLOR_RGB2Lab)

    mt = np.mean(target_lab, axis=(0, 1))
    stdt = np.std(target_lab, axis=(0, 1))

    norm_lab = np.copy(source_lab)

    norm_lab[:,:,0] = ((norm_lab[:,:,0]-ms[0])*(stdt[0]/stds[0]))+mt[0]
    norm_lab[:,:,1] = ((norm_lab[:,:,1]-ms[1])*(stdt[1]/stds[1]))+mt[1]
    norm_lab[:,:,2] = ((norm_lab[:,:,2]-ms[2])*(stdt[2]/stds[2]))+mt[2]

    norm_image = cv2.cvtColor(norm_lab, cv2.COLOR_Lab2RGB)
    
    if background_params is not None:
        weight_mask = (source_lab[:,:,0] - background_params[1]) / background_params[0]
        weight_mask[weight_mask < 0] = 0
        weight_mask[weight_mask > 1] = 1
        weight_mask = np.tile(weight_mask[:, :, np.newaxis], (1, 1, 3))

        norm_image = (source_image*weight_mask + norm_image*(1-weight_mask))
        
    return norm_image
