from norm_cws import norm_cws_by_stats_batch

tile_path='../../../tiles'
mask_path='../../../masks'
out_path='../../../norm'

target_path='../../../ref/tiles'
target_mask_path='../../../ref/masks'

norm_cws_by_stats_batch(tile_path, target_path, out_path, mask_path, target_mask_path, file_pattern='*.jpg')
