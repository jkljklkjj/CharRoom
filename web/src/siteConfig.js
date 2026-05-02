// 全站可配置项（简单单文件配置）
const siteConfig = {
	ICP_NUMBER: '粤ICP备2026049055号',
	RELEASE_BASE: 'https://github.com/jkljklkjj/CharRoom/releases/latest',
	BUCKET_URL: 'https://dl.chatlite.xin/cloud-disk/',
	INSTALLER_URLS: {
        android: `https://dl.chatlite.xin/cloud-disk/chatlite.apk`,
        linux: `https://dl.chatlite.xin/cloud-disk/chatlite.deb`,
        macos: `https://dl.chatlite.xin/cloud-disk/chatlite.dmg`,
        windows: `https://dl.chatlite.xin/cloud-disk/chatlite.msi`
    },
	// 可配置项：作者邮箱与支付二维码图片路径
	AUTHOR_EMAIL: '2998568539@qq.com',
	PAYMENT_QRCODES: {
		alipay: '/src/assets/money-alipay.jpg',
		wechat: '/src/assets/money-wechat.jpg'
	}
}

export default siteConfig
