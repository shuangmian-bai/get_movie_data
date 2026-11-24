"""去水印插件子包"""
from ad_filter.remover.base import Remover
from ad_filter.remover.delogo import DelogoRemover
from ad_filter.remover.top_banner import TopBannerRemover

__all__ = ["Remover", "DelogoRemover", "TopBannerRemover"]
