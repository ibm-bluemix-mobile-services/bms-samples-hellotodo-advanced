//
//  AppDelegate.m
//  helloTodoAdvanced
//
//  Created by Joshua Alger on 1/12/16.
//  Copyright © 2016 IBM. All rights reserved.
//

#import "AppDelegate.h"
#import <IMFCore/IMFCore.h>
#import <FacebookSDK/FacebookSDK.h>
#import <IMFFacebookAuthentication/IMFFacebookAuthenticationHandler.h>
#import <IMFPush/IMFPush.h>
#import "ViewController.h"

@interface AppDelegate ()

@end

@implementation AppDelegate


- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    // Replace the backendRoute and backendGUID parameters in the initialization API below
    
    // You can find your backendRoute and backendGUID in the Mobile Options section on top of your
    // Bluemix application dashboard
    
    // Make sure to use https protocol if you're targeting iOS9, otherwise you might not be able to connect to your backend app
    // Read more about App Transport Security here
    // https://developer.apple.com/library/prerelease/ios/releasenotes/General/WhatsNewIniOS/Articles/iOS9.html
    
    IMFClient *imfClient = [IMFClient sharedInstance];
    [imfClient initializeWithBackendRoute:@"<Application_ID>" backendGUID:@"<Application_Route"];
    [[IMFFacebookAuthenticationHandler sharedInstance] registerWithDefaultDelegate];
      return YES;
}

- (void)applicationWillResignActive:(UIApplication *)application {
    // Sent when the application is about to move from active to inactive state. This can occur for certain types of temporary interruptions (such as an incoming phone call or SMS message) or when the user quits the application and it begins the transition to the background state.
    // Use this method to pause ongoing tasks, disable timers, and throttle down OpenGL ES frame rates. Games should use this method to pause the game.
}

- (void)applicationDidEnterBackground:(UIApplication *)application {
    // Use this method to release shared resources, save user data, invalidate timers, and store enough application state information to restore your application to its current state in case it is terminated later.
    // If your application supports background execution, this method is called instead of applicationWillTerminate: when the user quits.
}

- (void)applicationWillEnterForeground:(UIApplication *)application {
    // Called as part of the transition from the background to the inactive state; here you can undo many of the changes made on entering the background.
}

- (void)applicationDidBecomeActive:(UIApplication *)application {
    // Restart any tasks that were paused (or not yet started) while the application was inactive. If the application was previously in the background, optionally refresh the user interface.
}

- (void)applicationWillTerminate:(UIApplication *)application {
    // Called when the application is about to terminate. Save data if appropriate. See also applicationDidEnterBackground:.
}

-(void) application:(UIApplication *)application didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceToken{
    //initialize Push SDK
    IMFPushClient* push = [IMFPushClient sharedInstance];
    //register device with token
    [push registerDeviceToken:deviceToken completionHandler:^(IMFResponse *response, NSError *error) {
        NSString *message=@"";
        
        if (error){
            message = [NSString stringWithFormat:@"Error registering for push notifications: %@", error.description];
            NSLog(@"%@",message);
        } else {
            message=@"Successfully registered for push notifications";
            NSLog(@"%@",message);
        }
    }];
}

- (void)application:(UIApplication *)app didFailToRegisterForRemoteNotificationsWithError:(NSError *)error {
    NSString  *message = [NSString stringWithFormat:@"Error registering for push notifications: %@", error.description];
    NSLog(@"Registering for Push notifications: %@",message);
    }

- (void) application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo {
    //the notification object
    NSDictionary *pushNotification = [[userInfo objectForKey:@"aps"] objectForKey:@"alert"];
    //the message of the notification
    NSString *message = [pushNotification objectForKey:@"body"];
    //show an alert with the push notification contents
    [self showAlert:@"Received a Push Notification" :message];

    
}
-(void)showAlert:(NSString *) title :(NSString*) message{
    UIAlertView *alertView = [[UIAlertView alloc] initWithTitle:title message:message delegate:self cancelButtonTitle:@"Dismiss" otherButtonTitles:nil];
    [alertView show];
}


- (BOOL)application:(UIApplication *)application
            openURL:(NSURL *)url
  sourceApplication:(NSString *)sourceApplication
         annotation:(id)annotation {
    return [FBAppCall handleOpenURL:url sourceApplication:sourceApplication];
}

@end
