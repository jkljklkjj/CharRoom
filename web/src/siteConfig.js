// 全站可配置项（简单单文件配置）
const siteConfig = {
	ICP_NUMBER: '粤ICP备2026049055号',
	RELEASE_BASE: 'https://github.com/jkljklkjj/CharRoom/releases/latest',
	RELEASE_VERSION: 'build-25239911453',
	INSTALLER_URLS: {
		android: 'https://kkgithub.com/jkljklkjj/CharRoom/releases/download/{RELEASE_VERSION}/chatlite.apk',
		linux: 'https://kkgithub.com/jkljklkjj/CharRoom/releases/download/{RELEASE_VERSION}/chatlite.deb',
		macos: 'https://kkgithub.com/jkljklkjj/CharRoom/releases/download/{RELEASE_VERSION}/chatlite.dmg',
		windows: 'https://kkgithub.com/jkljklkjj/CharRoom/releases/download/{RELEASE_VERSION}/chatlite.msi'
	},
	// 可配置项：作者邮箱与支付二维码图片路径
	AUTHOR_EMAIL: '2998568539@qq.com',
	PAYMENT_QRCODES: {
		alipay: '/src/assets/money-alipay.jpg',
		wechat: '/src/assets/money-wechat.jpg'
	}
}

export default siteConfig
